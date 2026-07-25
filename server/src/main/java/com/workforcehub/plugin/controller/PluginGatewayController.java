package com.workforcehub.plugin.controller;

import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.runtime.InvocationContextBinder;
import com.workforcehub.plugin.runtime.PluginManagerService;
import com.workforcehub.plugin.runtime.PluginPermissionService;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.plugin.sdk.ServiceResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 插件动作调用网关。
 * 统一入口，校验插件状态、动作、权限，绑定 InvocationContext。
 */
@RestController
@RequestMapping("/api/plugin-gateway")
public class PluginGatewayController {

    private final PluginManagerService pluginManagerService;
    private final InvocationContextBinder contextBinder;
    private final PluginPermissionService permissionService;

    public PluginGatewayController(
            PluginManagerService pluginManagerService,
            InvocationContextBinder contextBinder,
            PluginPermissionService permissionService) {
        this.pluginManagerService = pluginManagerService;
        this.contextBinder = contextBinder;
        this.permissionService = permissionService;
    }

    /**
     * 执行插件动作。
     */
    @PostMapping("/{pluginId}/actions/{actionCode}")
    public ResponseEntity<ServiceResult<Object>> executeAction(
            @PathVariable String pluginId,
            @PathVariable String actionCode,
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        // 1. 检查插件是否存在
        var pluginOpt = pluginManagerService.findActionHandler(pluginId, actionCode);
        if (pluginOpt.isEmpty()) {
            // 区分：插件不存在 vs 动作不存在
            try {
                pluginManagerService.getPlugin(pluginId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ServiceResult.fail(4004, "动作不存在: " + actionCode));
            } catch (RuntimeException e) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ServiceResult.fail(4004, "插件不存在: " + pluginId));
            }
        }

        // 2. 检查插件状态
        var plugin = pluginManagerService.getPlugin(pluginId);
        if (plugin.getStatus() != PluginStatus.ACTIVE) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ServiceResult.fail(4009, "插件未启用，当前状态: " + plugin.getStatus()));
        }

        PluginActionHandler handler = pluginOpt.get();

        // 3. 绑定调用上下文（不可伪造）
        InvocationContext ctx = contextBinder.bindFromRequest(request, pluginId);

        // 4. 权限校验（插件能力 + 调用者权限）
        String requiredCapability = handler.getDescriptor().requiredCapability();
        if (requiredCapability != null && !requiredCapability.isBlank()) {
            try {
                permissionService.checkPermission(ctx, pluginId, actionCode, requiredCapability);
            } catch (PluginPermissionService.PermissionDeniedException e) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ServiceResult.fail(4003, e.getMessage()));
            }
        }

        // 5. 执行动作
        try {
            ServiceResult<Object> result = handler.execute(ctx, params);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ServiceResult.fail(5000, "动作执行失败: " + e.getMessage()));
        }
    }

    /**
     * 错误处理。
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ServiceResult<Object>> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ServiceResult.fail(5000, e.getMessage()));
    }
}
