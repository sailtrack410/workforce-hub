package com.workforcehub.plugin;

import com.workforcehub.WorkforceHubApplication;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.runtime.PluginLifecycleGuard;
import com.workforcehub.plugin.runtime.PluginManagerService;
import com.workforcehub.plugin.runtime.WorkforcePluginManager;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PluginRestartProcessProbe {

    private static final String PLUGIN_ID = "hello-plugin";
    private static final String ACTION_CODE = "hello.greet";

    private PluginRestartProcessProbe() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || !("verify".equals(args[0]) || "uninstall".equals(args[0]))) {
            throw new IllegalArgumentException("探针模式必须是 verify 或 uninstall");
        }

        try (ConfigurableApplicationContext context =
                new SpringApplicationBuilder(WorkforceHubApplication.class)
                    .web(WebApplicationType.SERVLET)
                    .run(args)) {
            for (var propertySource : context.getEnvironment().getPropertySources()) {
                require(!propertySource.getName().contains("application.yml"),
                    "子 JVM 仍加载默认 application.yml: " + propertySource.getName());
            }
            PluginManagerService service = context.getBean(PluginManagerService.class);
            WorkforcePluginManager manager = context.getBean(WorkforcePluginManager.class);
            PluginLifecycleGuard guard = context.getBean(PluginLifecycleGuard.class);

            require(service.getPlugin(PLUGIN_ID).getStatus() == PluginStatus.ACTIVE,
                "数据库插件状态不是 ACTIVE");
            PluginWrapper wrapper = manager.getPlugin(PLUGIN_ID);
            require(wrapper != null, "PF4J 未发现插件");
            require(wrapper.getPluginState() == PluginState.STARTED, "PF4J 插件未进入 STARTED");

            Path snapshotPath = Path.of(service.getPlugin(PLUGIN_ID).getJarPath())
                .toAbsolutePath()
                .normalize();
            require(snapshotPath.startsWith(
                manager.getPluginsRoot().toAbsolutePath().normalize().resolve(".snapshots")
            ), "恢复路径不是宿主快照目录");
            require(Files.isRegularFile(snapshotPath), "宿主插件快照不存在");
            require(snapshotPath.equals(wrapper.getPluginPath().toAbsolutePath().normalize()),
                "PF4J 加载路径与数据库快照不一致");

            var handlers = manager.getExtensions(PluginActionHandler.class, PLUGIN_ID);
            require(handlers.size() == 1, "动作扩展数量不是 1");
            require(ACTION_CODE.equals(handlers.get(0).getDescriptor().actionCode()),
                "动作扩展编码不匹配");
            require(service.findActionHandler(PLUGIN_ID, ACTION_CODE).isPresent(),
                "宿主服务未发现动作扩展");
            require(guard.tryAcquire(PLUGIN_ID), "请求守卫未开放");
            guard.release(PLUGIN_ID);
            require(guard.getActiveCount(PLUGIN_ID) == 0, "请求守卫计数未归零");

            if ("uninstall".equals(args[0])) {
                PluginClassLoader classLoader = requirePluginClassLoader(manager);
                service.uninstall(PLUGIN_ID);

                require(service.getPlugin(PLUGIN_ID).getStatus() == PluginStatus.UNINSTALLED,
                    "数据库插件状态不是 UNINSTALLED");
                require(manager.getPlugin(PLUGIN_ID) == null, "卸载后 PF4J 仍保留插件");
                require(manager.getPluginClassLoader(PLUGIN_ID) == null,
                    "卸载后 PF4J 仍保留 ClassLoader");
                require(classLoader.isClosed(), "卸载后 PluginClassLoader 未关闭");
                require(manager.getExtensions(PluginActionHandler.class, PLUGIN_ID).isEmpty(),
                    "卸载后动作扩展未清理");
                require(!guard.tryAcquire(PLUGIN_ID), "卸载后请求守卫仍开放");
                require(guard.getActiveCount(PLUGIN_ID) == 0, "卸载后请求守卫计数未归零");
            }
        }
    }

    private static PluginClassLoader requirePluginClassLoader(WorkforcePluginManager manager) {
        ClassLoader classLoader = manager.getPluginClassLoader(PLUGIN_ID);
        require(classLoader instanceof PluginClassLoader, "插件 ClassLoader 类型不正确");
        return (PluginClassLoader) classLoader;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
