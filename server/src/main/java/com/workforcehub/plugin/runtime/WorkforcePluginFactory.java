package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.PluginContext;
import org.pf4j.Plugin;
import org.pf4j.PluginFactory;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginWrapper;

import java.util.function.Supplier;

/**
 * 使用带 PluginContext 参数的构造器创建插件。
 */
public final class WorkforcePluginFactory implements PluginFactory {

    private final Supplier<HostServiceRegistry> registrySupplier;

    public WorkforcePluginFactory(Supplier<HostServiceRegistry> registrySupplier) {
        this.registrySupplier = registrySupplier;
    }

    @Override
    public Plugin create(PluginWrapper pluginWrapper) {
        String pluginClassName = pluginWrapper.getDescriptor().getPluginClass();
        PluginContext context = new DefaultPluginContext(
            pluginWrapper.getPluginId(), registrySupplier.get());

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
