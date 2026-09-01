package com.workforcehub.plugin.controller;

import com.workforcehub.common.exception.BusinessErrorCode;
import com.workforcehub.common.response.R;
import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.runtime.PluginLifecycleGuard;
import com.workforcehub.plugin.runtime.PluginManagerService;
import com.workforcehub.plugin.runtime.PluginParamValidator;
import com.workforcehub.plugin.runtime.PluginPermissionService;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.InvocationContextBinder;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.plugin.sdk.ServiceResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * 插件动作调用网关。
 * 统一入口，校验插件状态、动作、权限，绑定 InvocationContext。
 * 响应与全站一致使用 R 包装：code=0 成功；插件动作自身失败码原样透传；网关校验失败用 BusinessErrorCode。
 */
@RestController
@RequestMapping("/api/plugin-gateway")
public class PluginGatewayController {

    private static final Logger log = LoggerFactory.getLogger(PluginGatewayController.class);

    private final PluginManagerService pluginManagerService;
    private final InvocationContextBinder contextBinder;
    private final PluginPermissionService permissionService;
    private final PluginParamValidator paramValidator;
    private final PluginLifecycleGuard lifecycleGuard;
    private final Set<String> allowedOrigins;

    public PluginGatewayController(
            PluginManagerService pluginManagerService,
            InvocationContextBinder contextBinder,
            PluginPermissionService permissionService,
            PluginParamValidator paramValidator,
            PluginLifecycleGuard lifecycleGuard,
            @Value("${workforce.plugin.gateway.csrf.allowed-origins}") String allowedOrigins) {
        this.pluginManagerService = pluginManagerService;
        this.contextBinder = contextBinder;
        this.permissionService = permissionService;
        this.paramValidator = paramValidator;
        this.lifecycleGuard = lifecycleGuard;
        this.allowedOrigins = parseAllowedOrigins(allowedOrigins);
    }

    /**
     * 执行插件动作。
     */
    @Operation(summary = "调用插件动作", operationId = "invokePluginAction")
    @PostMapping("/{pluginId}/actions/{actionCode}")
    public ResponseEntity<R<Object>> executeAction(
            @PathVariable String pluginId,
            @PathVariable String actionCode,
            @RequestBody(required = false) Map<String, Object> params,
            HttpServletRequest request) {

        if (!isTrustedRequestSource(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(R.fail(BusinessErrorCode.FORBIDDEN.code(), "请求来源不受信任"));
        }

        if (params == null) {
            params = Map.of();
        }

        // 1. 检查插件是否存在（不抛异常）
        PluginRow plugin;
        try {
            plugin = pluginManagerService.getPlugin(pluginId);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.fail(BusinessErrorCode.NOT_FOUND.code(), "插件不存在"));
        }

        // 2. 检查插件状态
        if (plugin.getStatus() != PluginStatus.ACTIVE) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(R.fail(BusinessErrorCode.CONFLICT.code(), "插件未启用"));
        }

        // 并发守卫：原子检查门开 + 计数（在状态确认之后）
        if (!lifecycleGuard.tryAcquire(pluginId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(R.fail(BusinessErrorCode.CONFLICT.code(), "插件正在停用"));
        }
        try {
            return doExecute(pluginId, actionCode, params, request);
        } finally {
            lifecycleGuard.release(pluginId);
        }
    }

    private ResponseEntity<R<Object>> doExecute(
            String pluginId, String actionCode, Map<String, Object> params, HttpServletRequest request) {

        // 4. 绑定调用上下文
        InvocationContext ctx;
        try {
            ctx = contextBinder.bindFromRequest(request, pluginId);
            // 5. 权限校验（三重：插件能力 + 动作授权 + 调用者权限）
            permissionService.checkPermission(ctx, pluginId, actionCode);
        } catch (PluginPermissionService.PermissionDeniedException | AccessDeniedException e) {
            log.debug("权限校验失败: pluginId={}, actionCode={}, reason={}", pluginId, actionCode, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(R.fail(BusinessErrorCode.FORBIDDEN.code(), "权限不足"));
        }

        // 3. 发现动作处理器
        Optional<PluginActionHandler> handlerOpt;
        try {
            handlerOpt = pluginManagerService.findActionHandler(pluginId, actionCode);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.fail(BusinessErrorCode.NOT_FOUND.code(), "插件不存在"));
        }
        if (handlerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.fail(BusinessErrorCode.NOT_FOUND.code(), "动作不存在"));
        }

        PluginActionHandler handler = handlerOpt.get();

        // 6. 参数校验（根据 inputSchema）
        List<String> paramErrors = paramValidator.validate(handler.getDescriptor(), params);
        if (!paramErrors.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(BusinessErrorCode.BAD_REQUEST.code(), String.join("; ", paramErrors)));
        }

        // 7. 执行动作
        try {
            ServiceResult<Object> result = handler.execute(ctx, params);
            if (result.getCode() != 0) {
                // 插件动作的业务失败：保留插件自身错误码，仅换成统一响应包装
                return ResponseEntity.ok(R.fail(result.getCode(), result.getMessage()));
            }
            return ResponseEntity.ok(R.ok(result.getData()));
        } catch (Exception e) {
            log.error("插件动作执行失败: pluginId={}, actionCode={}", pluginId, actionCode, e);
            // 不泄露内部信息
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(BusinessErrorCode.INTERNAL_ERROR.code(), "动作执行失败"));
        }
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Object>> handleInvalidJson(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(R.fail(BusinessErrorCode.BAD_REQUEST.code(), "请求参数格式错误"));
    }

    /**
     * 全局错误处理 - 不泄露内部信息。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Object>> handleException(Exception e) {
        log.error("网关异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(R.fail(BusinessErrorCode.INTERNAL_ERROR.code(), "服务内部错误"));
    }

    private boolean isTrustedRequestSource(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null) {
            String normalized = normalizeOrigin(origin, false);
            return normalized != null && allowedOrigins.contains(normalized);
        }

        String referer = request.getHeader(HttpHeaders.REFERER);
        if (referer == null) {
            return false;
        }
        String normalized = normalizeOrigin(referer, true);
        return normalized != null && allowedOrigins.contains(normalized);
    }

    private Set<String> parseAllowedOrigins(String configuredOrigins) {
        Set<String> parsed = new LinkedHashSet<>();
        Arrays.stream(configuredOrigins.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .forEach(value -> {
                String normalized = normalizeOrigin(value, false);
                if (normalized == null) {
                    throw new IllegalArgumentException("非法插件网关允许来源: " + value);
                }
                parsed.add(normalized);
            });
        return Set.copyOf(parsed);
    }

    private String normalizeOrigin(String value, boolean allowPath) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }

        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || uri.getUserInfo() != null) {
                return null;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            if (uri.getQuery() != null || uri.getFragment() != null) {
                return null;
            }
            if (!allowPath && uri.getPath() != null && !uri.getPath().isEmpty()) {
                return null;
            }

            int port = uri.getPort();
            if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }

            host = host.toLowerCase(Locale.ROOT);
            if (host.indexOf(':') >= 0) {
                host = "[" + host + "]";
            }
            return scheme + "://" + host + (port < 0 ? "" : ":" + port);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
