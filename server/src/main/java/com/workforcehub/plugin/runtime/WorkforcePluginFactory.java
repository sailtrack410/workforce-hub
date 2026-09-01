package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.PluginContext;
import com.workforcehub.plugin.sdk.PluginConfig;
import com.workforcehub.plugin.sdk.WorkforcePlugin;
import org.pf4j.PluginFactory;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginWrapper;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 使用带 PluginContext 参数的构造器创建插件。
 */
public final class WorkforcePluginFactory implements PluginFactory {

    private final Supplier<HostServiceRegistry> registrySupplier;
    private final Function<String, PluginConfig> configFactory;
    private final BiConsumer<String, WorkforcePlugin> instanceConsumer;

    public WorkforcePluginFactory(
            Supplier<HostServiceRegistry> registrySupplier,
            Function<String, PluginConfig> configFactory,
            BiConsumer<String, WorkforcePlugin> instanceConsumer) {
        this.registrySupplier = registrySupplier;
        this.configFactory = configFactory;
        this.instanceConsumer = instanceConsumer;
    }

    @Override
    public WorkforcePlugin create(PluginWrapper pluginWrapper) {
        String pluginClassName = pluginWrapper.getDescriptor().getPluginClass();
        PluginContext context = new DefaultPluginContext(
            pluginWrapper.getPluginId(),
            registrySupplier.get(),
            configFactory.apply(pluginWrapper.getPluginId()));

        try {
            Class<?> pluginClass = pluginWrapper.getPluginClassLoader().loadClass(pluginClassName);
            if (!WorkforcePlugin.class.isAssignableFrom(pluginClass)) {
                throw new PluginRuntimeException(
                    "插件主类必须继承 WorkforcePlugin: %s",
                    pluginClassName
                );
            }
            WorkforcePlugin plugin = pluginClass.asSubclass(WorkforcePlugin.class)
                .getConstructor(PluginWrapper.class, PluginContext.class)
                .newInstance(pluginWrapper, context);
            instanceConsumer.accept(pluginWrapper.getPluginId(), plugin);
            return plugin;
        } catch (ReflectiveOperationException exception) {
            throw new PluginRuntimeException(
                exception,
                "插件构造失败: %s",
                pluginClassName
            );
        }
    }
}
