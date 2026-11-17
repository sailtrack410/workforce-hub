package com.workforcehub.plugin;

import com.workforcehub.plugin.runtime.PluginPermissionService;
import com.workforcehub.plugin.runtime.WorkforcePluginManager;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PluginLifecycleTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadStartStopAndUnloadPluginWithHostContext() throws Exception {
        Path pluginJar = resolvePluginJar();
        WorkforcePluginManager pluginManager = new WorkforcePluginManager(
            tempDir.resolve("plugins").toString(), mock(PluginPermissionService.class));

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

    @Test
    void shouldReleaseHostAndPf4jReferencesAfterUnload() throws Exception {
        Path pluginJar = buildPluginJar();
        // 复制到 tempDir 内部，避免删除外部文件
        Path safeJar = tempDir.resolve("test-hello-plugin.jar");
        Files.copy(pluginJar, safeJar);

        WorkforcePluginManager pluginManager = new WorkforcePluginManager(
            tempDir.resolve("plugins").toString(), mock(PluginPermissionService.class));
        ReferenceQueue<ClassLoader> referenceQueue = new ReferenceQueue<>();
        try {
            WeakReference<ClassLoader> classLoaderReference = unloadAndReferenceClassLoader(
                pluginManager, safeJar, referenceQueue);
            assertClassLoaderCollected(classLoaderReference, referenceQueue);
        } finally {
            Reference.reachabilityFence(pluginManager);
        }

        // isClosed 是确定性主断言，文件移动仅为补充验证
        // JAR 可删除：先 move 到新路径再 delete，确保 ClassLoader 已释放
        Path movedJar = tempDir.resolve("moved-hello-plugin.jar");
        Files.move(safeJar, movedJar);
        assertTrue(Files.exists(movedJar), "move 后文件应存在");
        Files.delete(movedJar);
        assertFalse(Files.exists(movedJar), "delete 后文件应不存在");
        assertFalse(Files.exists(safeJar), "原路径也应不存在");
    }

    private WeakReference<ClassLoader> unloadAndReferenceClassLoader(
            WorkforcePluginManager pluginManager,
            Path safeJar,
            ReferenceQueue<ClassLoader> referenceQueue) {
        String pluginId = pluginManager.loadPlugin(safeJar);
        pluginManager.startPlugin(pluginId);

        PluginWrapper wrapper = pluginManager.getPlugin(pluginId);
        assertNotNull(wrapper, "start 后应有插件 wrapper");

        // 获取原始 ClassLoader
        // 使用 PF4J 公共状态验证 close() 已执行
        ClassLoader originalLoader = pluginManager.getPluginClassLoader(pluginId);
        assertNotNull(originalLoader, "start 后应有 ClassLoader");
        PluginClassLoader pluginClassLoader = assertInstanceOf(
            PluginClassLoader.class, originalLoader, "ClassLoader 应为 PluginClassLoader");

        // 验证扩展存在
        List<PluginActionHandler> handlers = pluginManager.getExtensions(PluginActionHandler.class, pluginId);
        assertFalse(handlers.isEmpty(), "start 后应有扩展");

        // stop + unload
        pluginManager.stopPlugin(pluginId);
        assertTrue(pluginManager.unloadPlugin(pluginId));

        // getPlugin 应为 null
        assertNull(pluginManager.getPlugin(pluginId), "unload 后 getPlugin 应为 null");

        // ClassLoader 已关闭（确定性主断言）
        assertTrue(pluginClassLoader.isClosed(), "unload 后 PluginClassLoader.isClosed() 应为 true");

        // ClassLoader 不再可用
        ClassLoader afterLoader = pluginManager.getPluginClassLoader(pluginId);
        assertNull(afterLoader, "unload 后 getPluginClassLoader 应返回 null");

        // 扩展查询应为空
        List<PluginActionHandler> afterHandlers = pluginManager.getExtensions(PluginActionHandler.class, pluginId);
        assertTrue(afterHandlers.isEmpty(), "unload 后扩展应为空");

        return new WeakReference<>(pluginClassLoader, referenceQueue);
    }

    private void assertClassLoaderCollected(
            WeakReference<ClassLoader> classLoaderReference,
            ReferenceQueue<ClassLoader> referenceQueue) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        Reference<? extends ClassLoader> enqueuedReference = null;
        while (enqueuedReference == null && System.nanoTime() < deadline) {
            System.gc();
            System.runFinalization();
            enqueuedReference = referenceQueue.remove(100);
        }
        assertSame(classLoaderReference, enqueuedReference,
            "unload 后 ClassLoader 应在 5 秒内进入 ReferenceQueue");
        assertNull(classLoaderReference.get(), "进入 ReferenceQueue 后 ClassLoader 应不可达");
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
        Path pluginSrc = Path.of("..", "plugins", "hello-plugin", "src", "main", "java",
            "com", "workforcehub", "plugin", "hello").toAbsolutePath().normalize();
        Path source1 = pluginSrc.resolve("HelloPlugin.java");
        Path source2 = pluginSrc.resolve("HelloActionHandler.java");
        Path classes = tempDir.resolve("hello-plugin-classes");
        Files.createDirectories(classes);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "生命周期测试需要 JDK，不能只使用 JRE");
        int result = compiler.run(
            null, null, null,
            "-classpath", System.getProperty("java.class.path"),
            "-d", classes.toString(),
            source1.toString(),
            source2.toString()
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
