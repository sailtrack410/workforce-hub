package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.HostService;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Workforce Hub 的 PF4J 管理器（Spring 单例）。
 * 全局只使用一个实例，由 Spring 注入。
 */
@Component
public final class WorkforcePluginManager extends DefaultPluginManager {

    private final HostServiceRegistry serviceRegistry;

    @Autowired
    public WorkforcePluginManager(
            @Value("${workforce.plugin.root:plugins}") String pluginsRoot,
            List<HostService> hostServices,
            PluginPermissionService permissionService) {
        super(Path.of(pluginsRoot));
        this.serviceRegistry = new HostServiceRegistry(hostServices, permissionService);
    }

    /**
     * 测试用构造器，无 HostService 注入。
     * permissionService 必须显式传入，fail closed。
     */
    public WorkforcePluginManager(String pluginsRoot, PluginPermissionService permissionService) {
        this(pluginsRoot, List.of(), permissionService);
    }

    @Override
    protected PluginFactory createPluginFactory() {
        return new WorkforcePluginFactory(() -> serviceRegistry);
    }

    boolean unloadPluginWithoutDependents(String pluginId) {
        return super.unloadPlugin(pluginId, false, false);
    }
}
