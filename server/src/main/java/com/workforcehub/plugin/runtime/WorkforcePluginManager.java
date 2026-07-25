package com.workforcehub.plugin.runtime;

import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Workforce Hub 的 PF4J 管理器（Spring 单例）。
 * 全局只使用一个实例，由 Spring 注入。
 */
@Component
public final class WorkforcePluginManager extends DefaultPluginManager {

    private InvocationContextBinder contextBinder;

    public WorkforcePluginManager(
            @Value("${workforce.plugin.root:plugins}") String pluginsRoot,
            InvocationContextBinder contextBinder) {
        super(Path.of(pluginsRoot));
        this.contextBinder = contextBinder;
    }

    /**
     * 设置上下文绑定器（用于测试或延迟初始化）。
     */
    public void setContextBinder(InvocationContextBinder contextBinder) {
        this.contextBinder = contextBinder;
    }

    @Override
    protected PluginFactory createPluginFactory() {
        // 如果 contextBinder 未设置，创建一个简单的默认实现
        InvocationContextBinder binder = contextBinder;
        if (binder == null) {
            binder = new InvocationContextBinder() {
                @Override
                public com.workforcehub.plugin.sdk.InvocationContext forPluginStartup(String pluginId) {
                    return new com.workforcehub.plugin.sdk.InvocationContext(
                        pluginId,
                        null,
                        com.workforcehub.plugin.sdk.InvocationContext.CallerType.PLUGIN,
                        UUID.randomUUID().toString(),
                        com.workforcehub.plugin.sdk.InvocationContext.TriggerSource.STARTUP
                    );
                }
            };
        }
        return new WorkforcePluginFactory(binder);
    }
}
