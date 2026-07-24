package com.workforcehub.plugin.runtime;

import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginFactory;

import java.nio.file.Path;

/**
 * Workforce Hub 的最小 PF4J 管理器。
 */
public final class WorkforcePluginManager extends DefaultPluginManager {

    public WorkforcePluginManager(Path pluginsRoot) {
        super(pluginsRoot);
    }

    @Override
    protected PluginFactory createPluginFactory() {
        return new WorkforcePluginFactory();
    }
}
