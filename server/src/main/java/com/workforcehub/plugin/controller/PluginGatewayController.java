package com.workforcehub.plugin.controller;

import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.runtime.InvocationContextBinder;
import com.workforcehub.plugin.runtime.PluginManagerService;
import com.workforcehub.plugin.runtime.PluginParamValidator;
import com.workforcehub.plugin.runtime.PluginPermissionService;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.plugin.sdk.ServiceResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 插件动作调用网关。
 * 统一入口，校验插件状态、动作、权限，绑定 InvocationContext。
 */
@RestController
@RequestMapping("/api/plugin-gateway")
public class PluginGatewayController {

    private static final Logger log = LoggerFactory.getLogger(PluginGatewayController.class);

    private final PluginManagerService pluginManagerService;
    private final InvocationContextBinder contextBinder;
    private final PluginPermissionService permissionService;
    private final PluginParamValidator paramValidator;

    public PluginGatewayController(
            PluginManagerService pluginManagerService,
            InvocationContextBinder contextBinder,
            PluginPermissionService permissionService,
            PluginParamValidator paramValidator) {
        this.pluginManagerService = pluginManagerService;
        this.contextBinder = contextBinder;
        this.permissionService = permissionService;
        this.paramValidator = paramValidator;
    }

    /**
     * 执行插件动作。
     */
    @PostMapping("/{pluginId}/actions/{actionCode}")
    public ResponseEntity<ServiceResult<Object>> executeAction(
            @PathVariable String pluginId,
            @PathVariable String actionCode,
            @RequestBody(required = false) Map<String, Object> params,
            HttpServletRequest request) {

        if (params == null) {
            params = Map.of();
        }

        // 1. 检查插件是否存在（不抛异常）
        PluginRow plugin;
        try {
            plugin = pluginManagerService.getPlugin(pluginId);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ServiceResult.fail(4004, "插件不存在"));
        }

        // 2. 检查插件状态
        if (plugin.getStatus() != PluginStatus.ACTIVE) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ServiceResult.fail(4009, "插件未启用"));
        }

        // 3. 发现动作处理器
        var handlerOpt = pluginManagerService.findActionHandler(pluginId, actionCode);
        if (handlerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ServiceResult.fail(4004, "动作不存在"));
        }

        PluginActionHandler handler = handlerOpt.get();

        // 4. 参数校验（根据 inputSchema）
        List<String> paramErrors = paramValidator.validate(handler.getDescriptor(), params);
        if (!paramErrors.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ServiceResult.fail(1000, String.join("; ", paramErrors)));
        }

        // 5. 绑定调用上下文
        InvocationContext ctx = contextBinder.bindFromRequest(request, pluginId);

        // 6. 权限校验（三重：插件能力 + 动作授权 + 调用者权限）
        String capabilityCode = handler.getDescriptor().requiredCapability();
        if (capabilityCode != null && !capabilityCode.isBlank()) {
            try {
                permissionService.checkPermission(ctx, pluginId, actionCode, capabilityCode);
            } catch (PluginPermissionService.PermissionDeniedException e) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ServiceResult.fail(4003, "权限不足"));
            }
        }

        // 7. 执行动作
        try {
            ServiceResult<Object> result = handler.execute(ctx, params);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("插件动作执行失败: pluginId={}, actionCode={}", pluginId, actionCode, e);
            // 不泄露内部信息
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ServiceResult.fail(5000, "动作执行失败"));
        }
    }

    /**
     * 全局错误处理 - 不泄露内部信息。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ServiceResult<Object>> handleException(Exception e) {
        log.error("网关异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ServiceResult.fail(5000, "服务内部错误"));
    }
}
