package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.HostService;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.PluginContext;

/**
 * 最小宿主插件上下文，后续按公共能力目录注册 HostService。
 */
public final class DefaultPluginContext implements PluginContext {

    private final String pluginId;
    private final InvocationContext invocationContext;

    public DefaultPluginContext(String pluginId, InvocationContextBinder binder) {
        this.pluginId = pluginId;
        this.invocationContext = binder.forPluginStartup(pluginId);
    }

    @Override
    public String getPluginId() {
        return pluginId;
    }

    @Override
    public InvocationContext getInvocationContext() {
        return invocationContext;
    }

    @Override
    public <T extends HostService> T getService(Class<T> type) {
        throw new IllegalArgumentException("宿主服务尚未注册: " + type.getName());
    }
}
