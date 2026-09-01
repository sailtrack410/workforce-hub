package com.workforcehub.plugin.sdk;

import java.util.UUID;

/**
 * 宿主侧 InvocationContext 工厂。
 * 放在 server 模块的 com.workforcehub.plugin.sdk 包中，
 * 可访问包级私有构造器，插件无法调用。
 */
public final class InvocationContextFactory {

    private InvocationContextFactory() {
    }

    public static InvocationContext forHttpRequest(
            String pluginId, Long userId, InvocationContext.CallerType callerType, String requestId) {
        return new InvocationContext(pluginId, userId, callerType,
            requestId != null ? requestId : UUID.randomUUID().toString(),
            InvocationContext.TriggerSource.HTTP_REQUEST);
    }

    public static InvocationContext forPluginStartup(String pluginId) {
        return new InvocationContext(pluginId, null, InvocationContext.CallerType.PLUGIN,
            UUID.randomUUID().toString(), InvocationContext.TriggerSource.STARTUP);
    }

    public static InvocationContext forScheduledTask(String pluginId) {
        return new InvocationContext(pluginId, null, InvocationContext.CallerType.PLUGIN,
            UUID.randomUUID().toString(), InvocationContext.TriggerSource.SCHEDULED_TASK);
    }
}
