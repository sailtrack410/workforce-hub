package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.PluginContext;
import org.pf4j.Plugin;
import org.pf4j.PluginFactory;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginWrapper;

/**
 * 使用带 PluginContext 参数的构造器创建插件。
 */
public final class WorkforcePluginFactory implements PluginFactory {

    private final InvocationContextBinder contextBinder;

    public WorkforcePluginFactory(InvocationContextBinder contextBinder) {
        this.contextBinder = contextBinder;
    }

    @Override
    public Plugin create(PluginWrapper pluginWrapper) {
        String pluginClassName = pluginWrapper.getDescriptor().getPluginClass();
        PluginContext context = new DefaultPluginContext(pluginWrapper.getPluginId(), contextBinder);

        try {
            Class<?> pluginClass = pluginWrapper.getPluginClassLoader().loadClass(pluginClassName);
            return (Plugin) pluginClass
                .getConstructor(PluginWrapper.class, PluginContext.class)
                .newInstance(pluginWrapper, context);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            throw new PluginRuntimeException(
                exception,
                "插件构造失败: %s",
                pluginClassName
            );
        }
    }
}
