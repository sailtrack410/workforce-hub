package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginEventSubscriptionRepository;
import com.workforcehub.plugin.repository.PluginOperationIdempotencyRepository;
import com.workforcehub.plugin.repository.PluginOperationIdempotencyRow;
import com.workforcehub.plugin.sdk.ErrorCodes;
import com.workforcehub.plugin.sdk.EventTypes;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.ServiceResult;
import com.workforcehub.plugin.sdk.service.EventService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * 事件订阅服务实现。
 */
@Service
public class DefaultEventService implements EventService {

    private static final Set<String> ALLOWED_EVENT_TYPES = Set.of(
        EventTypes.WORKFLOW_APPROVED,
        EventTypes.WORKFLOW_REJECTED,
        EventTypes.EMPLOYEE_HIRED,
        EventTypes.EMPLOYEE_LEFT,
        EventTypes.FILE_UPLOADED,
        EventTypes.ATTENDANCE_EXCEPTION
    );

    private static final int MAX_PLUGIN_ID_LENGTH = 64;
    private static final int MAX_FIELD_LENGTH = 128;
    private static final String EVENT_SUBSCRIBE_CAPABILITY = "EVENT_SUBSCRIBE";

    private final ObjectProvider<PluginManagerService> pluginManagerProvider;
    private final PluginEventSubscriptionRepository subscriptionRepository;
    private final PluginOperationIdempotencyRepository idempotencyRepository;
    private final PluginPermissionService permissionService;

    public DefaultEventService(
            ObjectProvider<PluginManagerService> pluginManagerProvider,
            PluginEventSubscriptionRepository subscriptionRepository,
            PluginOperationIdempotencyRepository idempotencyRepository,
            PluginPermissionService permissionService) {
        this.pluginManagerProvider = pluginManagerProvider;
        this.subscriptionRepository = subscriptionRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.permissionService = permissionService;
    }

    @Override
    @Transactional
    public ServiceResult<Void> subscribe(InvocationContext ctx, String eventType, String actionCode, String idempotencyKey) {
        ServiceResult<Void> base = validateBase(ctx, eventType, idempotencyKey);
        if (base != null) return base;

        if (actionCode == null || actionCode.isBlank()) {
            return ServiceResult.fail(ErrorCodes.INVALID_PARAM, "actionCode 不能为空");
        }
        if (actionCode.length() > MAX_FIELD_LENGTH) {
            return ServiceResult.fail(ErrorCodes.INVALID_PARAM, "actionCode 超长");
        }

        String pluginId = ctx.getPluginId();

        permissionService.checkPluginCapability(pluginId, EVENT_SUBSCRIBE_CAPABILITY);
        // 事件专项能力校验（在动作发现和幂等写入前）
        // eventType 已通过 ALLOWED_EVENT_TYPES 校验，能力码为 "EVENT_SUBSCRIBE_" + eventType
        permissionService.checkPluginCapability(pluginId, "EVENT_SUBSCRIBE_" + eventType);

        PluginManagerService pms = pluginManagerProvider.getIfAvailable();
        if (pms == null) {
            throw new IllegalStateException("PluginManagerService 不可用");
        }
        if (pms.findActionHandler(pluginId, actionCode).isEmpty()) {
            return ServiceResult.fail(ErrorCodes.STATE_CONFLICT, "插件动作不存在: " + actionCode);
        }

        String requestHash = computeHash(eventType, actionCode);

        try {
            idempotencyRepository.insert(pluginId, "EVENT_SUBSCRIBE", idempotencyKey, requestHash);
        } catch (DuplicateKeyException e) {
            return handleDuplicate(pluginId, "EVENT_SUBSCRIBE", idempotencyKey, requestHash);
        }

        subscriptionRepository.save(pluginId, eventType, actionCode);
        return ServiceResult.ok(null);
    }

    @Override
    @Transactional
    public ServiceResult<Void> unsubscribe(InvocationContext ctx, String eventType, String idempotencyKey) {
        ServiceResult<Void> base = validateBase(ctx, eventType, idempotencyKey);
        if (base != null) return base;

        String pluginId = ctx.getPluginId();
        permissionService.checkPluginCapability(pluginId, EVENT_SUBSCRIBE_CAPABILITY);
        String requestHash = computeHash(eventType);

        try {
            idempotencyRepository.insert(pluginId, "EVENT_UNSUBSCRIBE", idempotencyKey, requestHash);
        } catch (DuplicateKeyException e) {
            return handleDuplicate(pluginId, "EVENT_UNSUBSCRIBE", idempotencyKey, requestHash);
        }

        subscriptionRepository.delete(pluginId, eventType);
        return ServiceResult.ok(null);
    }

    private ServiceResult<Void> validateBase(InvocationContext ctx, String eventType, String idempotencyKey) {
        if (ctx == null) {
            return ServiceResult.fail(ErrorCodes.INVALID_PARAM, "ctx 不能为 null");
        }
        String pluginId = ctx.getPluginId();
        if (pluginId == null || pluginId.isBlank()) {
            return ServiceResult.fail(ErrorCodes.INVALID_PARAM, "pluginId 不能为空");
        }
        if (pluginId.length() > MAX_PLUGIN_ID_LENGTH) {
            return ServiceResult.fail(ErrorCodes.INVALID_PARAM, "pluginId 超长");
        }
        if (eventType == null || eventType.isBlank()) {
            return ServiceResult.fail(ErrorCodes.INVALID_PARAM, "eventType 不能为空");
        }
        if (eventType.length() > MAX_FIELD_LENGTH) {
            return ServiceResult.fail(ErrorCodes.INVALID_PARAM, "eventType 超长");
        }
        if (!ALLOWED_EVENT_TYPES.contains(eventType)) {
            return ServiceResult.fail(ErrorCodes.INVALID_PARAM, "不支持的事件类型: " + eventType);
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ServiceResult.fail(ErrorCodes.INVALID_PARAM, "idempotencyKey 不能为空");
        }
        if (idempotencyKey.length() > MAX_FIELD_LENGTH) {
            return ServiceResult.fail(ErrorCodes.INVALID_PARAM, "idempotencyKey 超长");
        }
        return null;
    }

    private ServiceResult<Void> handleDuplicate(String pluginId, String operationCode, String idempotencyKey, String requestHash) {
        var existing = idempotencyRepository.find(pluginId, operationCode, idempotencyKey);
        if (existing.isEmpty()) {
            throw new IllegalStateException("幂等记录插入失败但查询为空: " + pluginId + "/" + operationCode + "/" + idempotencyKey);
        }
        if (requestHash.equals(existing.get().requestHash())) {
            return ServiceResult.ok(null);
        }
        return ServiceResult.fail(ErrorCodes.DUPLICATE_OPERATION, "同幂等键已用于不同请求");
    }

    /**
     * 4字节大端长度前缀 + 原始UTF-8字节 SHA-256，输出 64 位小写十六进制。
     */
    static String computeHash(String... fields) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String field : fields) {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                md.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                md.update(bytes);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
