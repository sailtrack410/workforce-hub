package com.workforcehub.plugin.sdk;

import java.util.UUID;

/**
 * 测试用 InvocationContextBinder（不依赖 Spring）。
 */
public class TestInvocationContextBinder extends InvocationContextBinder {

    @Override
    public InvocationContext forPluginStartup(String pluginId) {
        return new InvocationContext(
            pluginId,
            null,
            InvocationContext.CallerType.PLUGIN,
            UUID.randomUUID().toString(),
            InvocationContext.TriggerSource.STARTUP
        );
    }

    public InvocationContext forHttpRequest(
            String pluginId,
            Long userId,
            InvocationContext.CallerType callerType,
            String requestId) {
        return new InvocationContext(
            pluginId,
            userId,
            callerType,
            requestId,
            InvocationContext.TriggerSource.HTTP_REQUEST
        );
    }
}
