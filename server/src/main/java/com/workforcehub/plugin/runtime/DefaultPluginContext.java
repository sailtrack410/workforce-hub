package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.HostService;
import com.workforcehub.plugin.sdk.PluginContext;

/**
 * 最小宿主插件上下文，后续按公共能力目录注册 HostService。
 */
public final class DefaultPluginContext implements PluginContext {

    private final String pluginId;
    private final HostServiceRegistry serviceRegistry;

    public DefaultPluginContext(String pluginId, HostServiceRegistry serviceRegistry) {
        this.pluginId = pluginId;
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    public String getPluginId() {
        return pluginId;
    }

    @Override
    public <T extends HostService> T getService(Class<T> type) {
        return serviceRegistry.getRequired(type, pluginId);
    }
}
