package com.workforcehub.plugin.hello;

import com.workforcehub.plugin.sdk.PluginContext;
import com.workforcehub.plugin.sdk.WorkforcePlugin;
import org.pf4j.PluginWrapper;

/**
 * 验证 PF4J 生命周期和宿主上下文注入的最小插件。
 */
public final class HelloPlugin extends WorkforcePlugin {

    private boolean started;
    private boolean stopped;

    public HelloPlugin(PluginWrapper wrapper, PluginContext context) {
        super(wrapper, context);
    }

    @Override
    public void start() {
        started = true;
    }

    @Override
    public void stop() {
        stopped = true;
    }

    public String getContextPluginId() {
        return getContext().getPluginId();
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isStopped() {
        return stopped;
    }
}
