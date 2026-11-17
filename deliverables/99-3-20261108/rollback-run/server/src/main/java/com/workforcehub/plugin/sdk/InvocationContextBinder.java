package com.workforcehub.plugin.sdk;

import com.workforcehub.auth.security.WfhUserDetails;
import com.workforcehub.common.enums.UserType;
import com.workforcehub.common.util.CurrentUserHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * 调用上下文绑定器 - 由宿主创建，插件无法伪造。
 */
@Component
public class InvocationContextBinder {

    InvocationContextBinder() {
    }

    /**
     * 从 HTTP 请求绑定调用上下文。
     */
    public InvocationContext bindFromRequest(HttpServletRequest request, String pluginId) {
        WfhUserDetails user = CurrentUserHolder.current();
        if (user == null) {
            throw new AccessDeniedException("调用身份无效");
        }

        InvocationContext.CallerType callerType = user.getUserType() == UserType.ADMIN
            ? InvocationContext.CallerType.ADMIN
            : InvocationContext.CallerType.USER;

        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        return new InvocationContext(
            pluginId,
            user.getUserId(),
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

    /**
     * 为事件回调创建上下文。
     */
    public InvocationContext forEventCallback(String pluginId, DomainEvent event) {
        Objects.requireNonNull(event, "event");

        Long userId = null;
        if (!"system".equals(event.triggeredBy())) {
            try {
                userId = Long.valueOf(event.triggeredBy());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("triggeredBy 必须为用户ID或system", e);
            }
        }

        return new InvocationContext(
            pluginId,
            userId,
            InvocationContext.CallerType.PLUGIN,
            event.eventId(),
            InvocationContext.TriggerSource.EVENT_CALLBACK
        );
    }
}
