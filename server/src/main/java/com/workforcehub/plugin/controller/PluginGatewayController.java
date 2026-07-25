package com.workforcehub.plugin.controller;

import com.workforcehub.plugin.runtime.InvocationContextBinder;
import com.workforcehub.plugin.runtime.PluginManagerService;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.plugin.sdk.ServiceResult;
import jakarta.servlet.http.HttpServletRequest;
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

    public PluginGatewayController(
            PluginManagerService pluginManagerService,
            InvocationContextBinder contextBinder) {
        this.pluginManagerService = pluginManagerService;
        this.contextBinder = contextBinder;
    }

    /**
     * 执行插件动作。
     *
     * @param pluginId   插件ID
     * @param actionCode 动作编码
     * @param params     动作参数
     * @param request    HTTP 请求
     * @return 执行结果
     */
    @PostMapping("/{pluginId}/actions/{actionCode}")
    public ServiceResult<Object> executeAction(
            @PathVariable String pluginId,
            @PathVariable String actionCode,
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {

        // 1. 发现动作处理器（内部校验插件状态）
        PluginActionHandler handler = pluginManagerService.findActionHandler(pluginId, actionCode)
            .orElseThrow(() -> new RuntimeException(
                "动作不存在: pluginId=" + pluginId + ", actionCode=" + actionCode));

        // 2. 绑定调用上下文（不可伪造）
        InvocationContext ctx = contextBinder.bindFromRequest(request, pluginId);

        // 3. 校验权限（插件能力 + 用户权限）
        String requiredCapability = handler.getDescriptor().requiredCapability();
        if (requiredCapability != null && !requiredCapability.isBlank()) {
            // TODO: 实现真正的权限校验
            // permissionService.checkPermission(ctx, requiredCapability);
        }

        // 4. 执行动作
        return handler.execute(ctx, params);
    }
}
