package com.workforcehub.plugin.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workforcehub.plugin.repository.PluginActionIdempotencyRepository;
import com.workforcehub.plugin.repository.PluginActionIdempotencyRepository.ClaimScope;
import com.workforcehub.plugin.repository.PluginActionIdempotencyRow;
import com.workforcehub.plugin.sdk.ActionDescriptor;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.plugin.sdk.ServiceResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP 插件动作幂等编排。
 */
@Service
public class PluginActionIdempotencyService {

    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String COMPLETED = "COMPLETED";
    private static final Duration RESPONSE_TTL = Duration.ofHours(24);
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final int MAX_CLAIM_ATTEMPTS = 3;
    private static final ObjectMapper HASH_MAPPER = new ObjectMapper()
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private final PluginActionIdempotencyRepository repository;
    private final ObjectMapper objectMapper;
    private final PluginAuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public PluginActionIdempotencyService(
            PluginActionIdempotencyRepository repository,
            ObjectMapper objectMapper,
            PluginAuditService auditService,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ServiceResult<Object> execute(
            String pluginId,
            String actionCode,
            String idempotencyKey,
            InvocationContext ctx,
            Map<String, Object> params,
            PluginActionHandler handler) {
        Thread requestThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = requestThread.getContextClassLoader();
        boolean originallyInterrupted = requestThread.isInterrupted();
        try {
            if (ctx.getUserId() == null) {
                throw new IllegalStateException("HTTP 插件动作缺少调用者身份");
            }
            if (!pluginId.equals(ctx.getPluginId())
                    || ctx.getSource() != InvocationContext.TriggerSource.HTTP_REQUEST) {
                throw new IllegalStateException("HTTP 插件动作上下文不匹配");
            }

            ActionDescriptor descriptor;
            try {
                descriptor = handler.getDescriptor();
            } finally {
                useHostThreadState(requestThread);
            }
            if (!descriptor.idempotent()) {
                throw new IllegalStateException("HTTP 插件动作必须声明为幂等");
            }
            ClaimScope scope = new ClaimScope(
                pluginId,
                actionCode,
                ctx.getCallerType().name(),
                ctx.getUserId(),
                idempotencyKey
            );
            String requestHash = requestHash(params);
            ClaimResult claim = claim(scope, requestHash);

            if (claim.status() == ClaimStatus.REPLAY) {
                return readResponse(claim.responseJson());
            }
            if (claim.status() == ClaimStatus.IN_PROGRESS) {
                throw new ActionInProgressException();
            }
            if (claim.status() == ClaimStatus.CONFLICT) {
                throw new IdempotencyConflictException();
            }

            ServiceResult<Object> result = null;
            Throwable handlerError = null;
            try {
                result = handler.execute(ctx, params);
            } catch (Throwable e) {
                handlerError = e;
            } finally {
                useHostThreadState(requestThread);
            }
            if (handlerError != null) {
                rethrowIfFatal(handlerError);
                auditFailureKeepingClaim(ctx, pluginId, actionCode, handlerError);
                if (handlerError instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("插件动作执行失败", handlerError);
            }

            if (result == null) {
                IllegalStateException error = new IllegalStateException("插件动作返回 null");
                auditFailureKeepingClaim(ctx, pluginId, actionCode, error);
                throw error;
            }
            if (result.isRetryable()) {
                release(scope, requestHash, claim.claimToken(), ctx, pluginId, actionCode, result);
                return result;
            }

            String responseJson = null;
            ServiceResult<Object> replayableResult = null;
            Throwable responseError = null;
            try {
                try {
                    responseJson = writeResponse(result);
                } finally {
                    useHostThreadState(requestThread);
                }
                replayableResult = readResponse(responseJson);
            } catch (Throwable e) {
                responseError = e;
            } finally {
                useHostThreadState(requestThread);
            }
            if (responseError != null) {
                rethrowIfFatal(responseError);
                auditFailureKeepingClaim(ctx, pluginId, actionCode, responseError);
                if (responseError instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("插件动作响应处理失败", responseError);
            }
            complete(
                scope, requestHash, claim.claimToken(), responseJson, ctx, pluginId, actionCode, result);
            return replayableResult;
        } finally {
            requestThread.setContextClassLoader(originalContextClassLoader);
            if (originallyInterrupted) {
                requestThread.interrupt();
            } else {
                Thread.interrupted();
            }
        }
    }

    private ClaimResult claim(ClaimScope scope, String requestHash) {
        String claimToken = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < MAX_CLAIM_ATTEMPTS; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(status ->
                    repository.insertClaim(
                        scope.pluginId(),
                        scope.actionCode(),
                        scope.callerType(),
                        scope.callerId(),
                        scope.idempotencyKey(),
                        requestHash,
                        claimToken
                    )
                );
                return ClaimResult.claimed(claimToken);
            } catch (DuplicateKeyException e) {
                ClaimResult result = transactionTemplate.execute(status -> {
                    PluginActionIdempotencyRow existing = repository.findForUpdate(
                        scope.pluginId(), scope.actionCode(), scope.callerType(), scope.callerId(),
                        scope.idempotencyKey()
                    ).orElse(null);
                    if (existing == null) {
                        return null;
                    }

                    if (COMPLETED.equals(existing.status())) {
                        if (existing.expired()) {
                            return reuseExpiredCompleted(existing, requestHash, claimToken);
                        }
                        if (!requestHash.equals(existing.requestHash())) {
                            return ClaimResult.conflict();
                        }
                        if (existing.responseJson() == null) {
                            throw new IllegalStateException("已完成幂等记录缺少响应");
                        }
                        return ClaimResult.replay(existing.responseJson());
                    }

                    if (!IN_PROGRESS.equals(existing.status())) {
                        throw new IllegalStateException("未知幂等状态");
                    }
                    if (!requestHash.equals(existing.requestHash())) {
                        return ClaimResult.conflict();
                    }
                    return ClaimResult.inProgress();
                });
                if (result != null) {
                    return result;
                }
            }
        }
        throw new IllegalStateException("幂等记录并发变化，请重试");
    }

    private ClaimResult reuseExpiredCompleted(
            PluginActionIdempotencyRow existing,
            String requestHash,
            String claimToken) {
        if (repository.reclaimCompleted(existing, requestHash, claimToken) != 1) {
            throw new IllegalStateException("过期幂等响应复用失败");
        }
        return ClaimResult.claimed(claimToken);
    }

    private void complete(
            ClaimScope scope,
            String requestHash,
            String claimToken,
            String responseJson,
            InvocationContext context,
            String pluginId,
            String actionCode,
            ServiceResult<Object> result) {
        transactionTemplate.executeWithoutResult(status -> {
            int updated = repository.complete(
                scope,
                requestHash,
                claimToken,
                responseJson,
                RESPONSE_TTL.toSeconds()
            );
            if (updated != 1) {
                throw new IllegalStateException("幂等响应写入失败");
            }
            auditService.writeAction(
                context, pluginId, actionCode, result.getCode(), result.isRetryable());
        });
    }

    private void release(
            ClaimScope scope,
            String requestHash,
            String claimToken,
            InvocationContext context,
            String pluginId,
            String actionCode,
            ServiceResult<Object> result) {
        transactionTemplate.executeWithoutResult(status -> {
            int deleted = repository.release(scope, requestHash, claimToken);
            if (deleted != 1) {
                throw new IllegalStateException("幂等占用释放失败");
            }
            auditService.writeAction(
                context, pluginId, actionCode, result.getCode(), result.isRetryable());
        });
    }

    private void auditFailureKeepingClaim(
            InvocationContext context,
            String pluginId,
            String actionCode,
            Throwable originalError) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                auditService.writeActionFailure(context, pluginId, actionCode));
        } catch (RuntimeException auditError) {
            originalError.addSuppressed(auditError);
        }
    }

    private String requestHash(Map<String, Object> params) {
        try {
            byte[] canonicalJson = HASH_MAPPER.writeValueAsBytes(params);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalJson));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        } catch (Exception e) {
            throw new IllegalStateException("插件动作参数摘要失败", e);
        }
    }

    private static void useHostThreadState(Thread thread) {
        thread.setContextClassLoader(PluginActionIdempotencyService.class.getClassLoader());
        Thread.interrupted();
    }

    private static void rethrowIfFatal(Throwable throwable) {
        if (throwable instanceof VirtualMachineError error) {
            throw error;
        }
        if (throwable instanceof ThreadDeath threadDeath) {
            throw threadDeath;
        }
    }

    private String writeResponse(ServiceResult<Object> result) {
        try {
            BoundedByteArrayOutputStream response = new BoundedByteArrayOutputStream(MAX_RESPONSE_BYTES);
            objectMapper.writeValue(response, result);
            return response.toString(StandardCharsets.UTF_8);
        } catch (ResponseTooLargeException e) {
            throw new IllegalStateException("插件动作响应超过 256 KiB", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("插件动作响应序列化失败", e);
        }
    }

    private ServiceResult<Object> readResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode codeNode = root == null ? null : root.get("code");
            JsonNode messageNode = root == null ? null : root.get("message");
            JsonNode retryableNode = root == null ? null : root.get("retryable");
            JsonNode dataNode = root == null ? null : root.get("data");
            if (root == null || !root.isObject()
                    || codeNode == null || !codeNode.isIntegralNumber() || !codeNode.canConvertToInt()
                    || messageNode == null || !messageNode.isTextual()
                    || retryableNode == null || !retryableNode.isBoolean()
                    || dataNode == null) {
                throw new IllegalStateException("插件动作幂等响应格式错误");
            }
            int code = codeNode.intValue();
            String message = messageNode.textValue();
            boolean retryable = retryableNode.booleanValue();
            Object data = dataNode.isNull()
                ? null
                : objectMapper.treeToValue(dataNode, Object.class);
            if (code == 0) {
                if (retryable) {
                    throw new IllegalStateException("插件动作幂等响应格式错误");
                }
                return ServiceResult.ok(data);
            }
            if (data != null) {
                throw new IllegalStateException("插件动作幂等响应格式错误");
            }
            return ServiceResult.fail(code, message, retryable);
        } catch (Exception e) {
            throw new IllegalStateException("插件动作幂等响应损坏", e);
        }
    }

    private enum ClaimStatus {
        CLAIMED,
        REPLAY,
        IN_PROGRESS,
        CONFLICT
    }

    private record ClaimResult(ClaimStatus status, String claimToken, String responseJson) {

        private static ClaimResult claimed(String claimToken) {
            return new ClaimResult(ClaimStatus.CLAIMED, claimToken, null);
        }

        private static ClaimResult replay(String responseJson) {
            return new ClaimResult(ClaimStatus.REPLAY, null, responseJson);
        }

        private static ClaimResult inProgress() {
            return new ClaimResult(ClaimStatus.IN_PROGRESS, null, null);
        }

        private static ClaimResult conflict() {
            return new ClaimResult(ClaimStatus.CONFLICT, null, null);
        }
    }

    public static final class ActionInProgressException extends RuntimeException {
    }

    public static final class IdempotencyConflictException extends RuntimeException {
    }

    private static final class BoundedByteArrayOutputStream extends ByteArrayOutputStream {

        private final int maxBytes;

        private BoundedByteArrayOutputStream(int maxBytes) {
            super(Math.min(maxBytes, 1024));
            this.maxBytes = maxBytes;
        }

        @Override
        public synchronized void write(int value) {
            ensureBoundedCapacity(1);
            super.write(value);
        }

        @Override
        public synchronized void write(byte[] values, int offset, int length) {
            ensureBoundedCapacity(length);
            super.write(values, offset, length);
        }

        private void ensureBoundedCapacity(int additionalBytes) {
            if (additionalBytes > maxBytes - count) {
                throw new ResponseTooLargeException();
            }
            int required = count + additionalBytes;
            if (required > buf.length) {
                int expanded = Math.min(maxBytes, Math.max(required, buf.length * 2));
                buf = Arrays.copyOf(buf, expanded);
            }
        }
    }

    private static final class ResponseTooLargeException extends RuntimeException {
    }
}
