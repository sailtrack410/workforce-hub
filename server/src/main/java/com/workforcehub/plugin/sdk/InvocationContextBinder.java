package com.workforcehub.plugin.sdk;

import java.util.UUID;

/**
 * 宿主侧调用上下文创建入口，不包含在 plugin-sdk JAR 中。
 */
public final class InvocationContextBinder {

    private InvocationContextBinder() {
    }

    public static InvocationContext forPluginStartup(String pluginId) {
        return new InvocationContext(
            pluginId,
            null,
            InvocationContext.CallerType.PLUGIN,
            UUID.randomUUID().toString(),
            InvocationContext.TriggerSource.STARTUP
        );
    }
}
