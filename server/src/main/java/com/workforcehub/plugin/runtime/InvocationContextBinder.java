package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.common.enums.UserType;
import com.workforcehub.auth.security.WfhUserDetails;
import com.workforcehub.common.util.CurrentUserHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 调用上下文绑定器 - 由宿主创建，插件无法伪造。
 */
@Component
public class InvocationContextBinder {

    /**
     * 从 HTTP 请求绑定调用上下文。
     */
    public InvocationContext bindFromRequest(HttpServletRequest request, String pluginId) {
        WfhUserDetails user = CurrentUserHolder.current();

        InvocationContext.CallerType callerType = InvocationContext.CallerType.PLUGIN;
        Long userId = null;

        if (user != null) {
            userId = user.getUserId();
            if (user.getUserType() == UserType.ADMIN) {
                callerType = InvocationContext.CallerType.ADMIN;
            } else {
                callerType = InvocationContext.CallerType.USER;
            }
        }

        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        return new InvocationContext(
            pluginId,
            userId,
            callerType,
            requestId,
            InvocationContext.TriggerSource.HTTP_REQUEST
        );
    }

    /**
     * 为插件启动创建上下文。
     */
    public InvocationContext forPluginStartup(String pluginId) {
        return new InvocationContext(
            pluginId,
            null,
            InvocationContext.CallerType.PLUGIN,
            UUID.randomUUID().toString(),
            InvocationContext.TriggerSource.STARTUP
        );
    }

    /**
     * 为定时任务创建上下文。
     */
    public InvocationContext forScheduledTask(String pluginId) {
        return new InvocationContext(
            pluginId,
            null,
            InvocationContext.CallerType.PLUGIN,
            UUID.randomUUID().toString(),
            InvocationContext.TriggerSource.SCHEDULED_TASK
        );
    }
}
