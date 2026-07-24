package com.workforcehub.plugin;

import com.workforcehub.plugin.runtime.WorkforcePluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLifecycleTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadStartStopAndUnloadPluginWithHostContext() throws Exception {
        Path pluginJar = resolvePluginJar();
        WorkforcePluginManager pluginManager = new WorkforcePluginManager(
            tempDir.resolve("plugins").toString());

        String pluginId = pluginManager.loadPlugin(pluginJar);
        assertEquals("hello-plugin", pluginId);

        PluginWrapper wrapper = pluginManager.getPlugin(pluginId);
        assertNotNull(wrapper);
        Object plugin = wrapper.getPlugin();
        assertEquals("hello-plugin", invoke(plugin, "getContextPluginId"));
        assertEquals(PluginState.RESOLVED, wrapper.getPluginState());
        assertFalse((Boolean) invoke(plugin, "isStarted"));

        assertEquals(PluginState.STARTED, pluginManager.startPlugin(pluginId));
        assertTrue((Boolean) invoke(plugin, "isStarted"));

        assertEquals(PluginState.STOPPED, pluginManager.stopPlugin(pluginId));
        assertTrue((Boolean) invoke(plugin, "isStopped"));

        assertTrue(pluginManager.unloadPlugin(pluginId));
        assertNull(pluginManager.getPlugin(pluginId));
    }

    private Path resolvePluginJar() throws IOException {
        String configuredJar = System.getProperty("hello.plugin.jar");
        if (configuredJar != null && !configuredJar.isBlank()) {
            Path jar = Path.of(configuredJar).toAbsolutePath().normalize();
            assertTrue(Files.isRegularFile(jar), "指定的 hello-plugin JAR 不存在: " + jar);
            return jar;
        }
        return buildPluginJar();
    }

    private Path buildPluginJar() throws IOException {
        Path source = Path.of("..", "plugins", "hello-plugin", "src", "main", "java",
            "com", "workforcehub", "plugin", "hello", "HelloPlugin.java").toAbsolutePath().normalize();
        Path classes = tempDir.resolve("hello-plugin-classes");
        Files.createDirectories(classes);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "生命周期测试需要 JDK，不能只使用 JRE");
        int result = compiler.run(
            null,
            null,
            null,
            "-classpath",
            System.getProperty("java.class.path"),
            "-d",
            classes.toString(),
            source.toString()
        );
        assertEquals(0, result, "hello-plugin 源码编译失败");

        Path descriptor = classes.resolve("plugin.properties");
        Files.copy(
            Path.of("..", "plugins", "hello-plugin", "src", "main", "resources", "plugin.properties")
                .toAbsolutePath().normalize(),
            descriptor
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

    private Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }
}
