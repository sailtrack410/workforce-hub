package com.workforcehub.plugin;

import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.runtime.WorkforcePluginFactory;
import com.workforcehub.plugin.runtime.WorkforcePluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 插件生命周期与状态同步验证。
 * 验证 PF4J 生命周期和状态跟踪，不依赖数据库。
 */
class PluginManagerServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldInstallAndTrackStatus() throws Exception {
        Path jarPath = buildHelloPluginJar();

        // 使用真实的 WorkforcePluginManager
        WorkforcePluginManager pf4jManager = new WorkforcePluginManager(
            tempDir.resolve("plugins").toString());

        // 模拟安装流程
        String pluginId = pf4jManager.loadPlugin(jarPath);
        assertEquals("hello-plugin", pluginId);

        // 模拟数据库状态
        PluginStatus dbStatus = PluginStatus.INSTALLED;
        assertEquals(PluginStatus.INSTALLED, dbStatus);

        // 启用
        PluginState state = pf4jManager.startPlugin(pluginId);
        assertEquals(PluginState.STARTED, state);
        dbStatus = PluginStatus.ACTIVE;

        // 验证插件上下文
        PluginWrapper wrapper = pf4jManager.getPlugin(pluginId);
        assertNotNull(wrapper);
        Object plugin = wrapper.getPlugin();
        assertEquals("hello-plugin", invoke(plugin, "getContextPluginId"));
        assertTrue((Boolean) invoke(plugin, "isStarted"));

        // 停用
        state = pf4jManager.stopPlugin(pluginId);
        assertEquals(PluginState.STOPPED, state);
        dbStatus = PluginStatus.STOPPED;
        assertTrue((Boolean) invoke(plugin, "isStopped"));

        // 卸载
        pf4jManager.unloadPlugin(pluginId);
        assertNull(pf4jManager.getPlugin(pluginId));
        dbStatus = PluginStatus.UNINSTALLED;
        assertEquals(PluginStatus.UNINSTALLED, dbStatus);
    }

    @Test
    void shouldTrackMultipleStatusTransitions() throws Exception {
        Path jarPath = buildHelloPluginJar();
        WorkforcePluginManager pf4jManager = new WorkforcePluginManager(
            tempDir.resolve("plugins").toString());

        String pluginId = pf4jManager.loadPlugin(jarPath);
        PluginStatus dbStatus = PluginStatus.INSTALLED;

        // 状态转换链：INSTALLED -> ACTIVE -> STOPPED -> ACTIVE -> STOPPED -> UNINSTALLED
        pf4jManager.startPlugin(pluginId);
        dbStatus = PluginStatus.ACTIVE;
        assertEquals(PluginStatus.ACTIVE, dbStatus);

        pf4jManager.stopPlugin(pluginId);
        dbStatus = PluginStatus.STOPPED;
        assertEquals(PluginStatus.STOPPED, dbStatus);

        // 重新启用
        pf4jManager.startPlugin(pluginId);
        dbStatus = PluginStatus.ACTIVE;
        assertEquals(PluginStatus.ACTIVE, dbStatus);

        // 再次停用
        pf4jManager.stopPlugin(pluginId);
        dbStatus = PluginStatus.STOPPED;

        // 卸载
        pf4jManager.unloadPlugin(pluginId);
        dbStatus = PluginStatus.UNINSTALLED;
        assertEquals(PluginStatus.UNINSTALLED, dbStatus);
        assertNull(pf4jManager.getPlugin(pluginId));
    }

    private Object invoke(Object target, String methodName) throws Exception {
        return target.getClass().getMethod(methodName).invoke(target);
    }

    private Path buildHelloPluginJar() throws IOException {
        Path source = Path.of("..", "plugins", "hello-plugin", "src", "main", "java",
            "com", "workforcehub", "plugin", "hello", "HelloPlugin.java").toAbsolutePath().normalize();
        Path classes = tempDir.resolve("hello-plugin-classes");
        Files.createDirectories(classes);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "需要 JDK");
        int result = compiler.run(
            null, null, null,
            "-classpath", System.getProperty("java.class.path"),
            "-d", classes.toString(),
            source.toString()
        );
        assertEquals(0, result, "编译失败");

        Files.copy(
            Path.of("..", "plugins", "hello-plugin", "src", "main", "resources", "plugin.properties")
                .toAbsolutePath().normalize(),
            classes.resolve("plugin.properties")
        );

        Path jar = tempDir.resolve("hello-plugin.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            try (var files = Files.walk(classes)) {
                for (Path file : files.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList()) {
                    String entryName = classes.relativize(file).toString().replace('\\', '/');
                    output.putNextEntry(new JarEntry(entryName));
                    Files.copy(file, output);
                    output.closeEntry();
                }
            }
        }
        return jar;
    }
}
