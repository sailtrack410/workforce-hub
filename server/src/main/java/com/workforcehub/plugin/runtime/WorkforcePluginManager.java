package com.workforcehub.plugin.runtime;

import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Workforce Hub 的 PF4J 管理器（Spring 单例）。
 * 全局只使用一个实例，由 Spring 注入。
 */
@Component
public final class WorkforcePluginManager extends DefaultPluginManager {

    public WorkforcePluginManager(
            @Value("${workforce.plugin.root:plugins}") String pluginsRoot) {
        super(Path.of(pluginsRoot));
    }

    @Override
    protected PluginFactory createPluginFactory() {
        return new WorkforcePluginFactory();
    }
}
