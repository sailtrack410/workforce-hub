package com.workforcehub.plugin.sdk;

import java.util.Objects;

/**
 * 由宿主绑定的插件调用上下文。
 */
public final class InvocationContext {

    private final String pluginId;
    private final Long userId;
    private final CallerType callerType;
    private final String requestId;
    private final TriggerSource source;

    public InvocationContext(
            String pluginId,
            Long userId,
            CallerType callerType,
            String requestId,
            TriggerSource source) {
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId");
        this.userId = userId;
        this.callerType = Objects.requireNonNull(callerType, "callerType");
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.source = Objects.requireNonNull(source, "source");
    }

    public String getPluginId() {
        return pluginId;
    }

    public Long getUserId() {
        return userId;
    }

    public CallerType getCallerType() {
        return callerType;
    }

    public String getRequestId() {
        return requestId;
    }

    public TriggerSource getSource() {
        return source;
    }

    public enum CallerType {
        USER,
        ADMIN,
        PLUGIN
    }

    public enum TriggerSource {
        HTTP_REQUEST,
        SCHEDULED_TASK,
        ASYNC_TASK,
        EVENT_CALLBACK,
        STARTUP
    }
}
