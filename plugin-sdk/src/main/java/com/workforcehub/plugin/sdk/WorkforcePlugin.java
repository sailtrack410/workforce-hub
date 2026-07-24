package com.workforcehub.plugin.sdk;

import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

/**
 * Workforce Hub 插件基类。
 */
public abstract class WorkforcePlugin extends Plugin {

    private final PluginContext context;

    protected WorkforcePlugin(PluginWrapper wrapper, PluginContext context) {
        super(wrapper);
        this.context = context;
    }

    protected final PluginContext getContext() {
        return context;
    }
}
