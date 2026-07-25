package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.InvocationContext;

import java.util.UUID;

/**
 * 测试用 InvocationContextBinder（不依赖 Spring）。
 */
public class TestInvocationContextBinder extends InvocationContextBinder {

    @Override
    public InvocationContext forPluginStartup(String pluginId) {
        return InvocationContext.create(
            pluginId,
            null,
            InvocationContext.CallerType.PLUGIN,
            UUID.randomUUID().toString(),
            InvocationContext.TriggerSource.STARTUP
        );
    }
}
