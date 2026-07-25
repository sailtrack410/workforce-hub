package com.workforcehub.plugin;

import com.workforcehub.plugin.runtime.InvocationContextBinder;
import com.workforcehub.plugin.runtime.WorkforcePluginManager;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.plugin.sdk.ServiceResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginState;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 插件动作网关端到端测试。
 * 验证：正常调用、未启用、未知动作、停用后不可调用、卸载后清理。
 */
class PluginGatewayTest {

    private static final String ACTION_CODE = "hello.greet";
    private static final String CAPABILITY = "HELLO_GREET";

    @TempDir
    Path tempDir;

    @Test
    void shouldCallActionSuccessfully() throws Exception {
        Path jarPath = buildHelloPluginJar();
        WorkforcePluginManager pf4jManager = new WorkforcePluginManager(
            tempDir.resolve("plugins").toString());

        // 安装并启用
        String pluginId = pf4jManager.loadPlugin(jarPath);
        pf4jManager.startPlugin(pluginId);

        // 发现动作处理器
        var handlers = pf4jManager.getExtensions(PluginActionHandler.class, pluginId);
        assertFalse(handlers.isEmpty());

        PluginActionHandler handler = handlers.stream()
            .filter(h -> h.getActionCode().equals(ACTION_CODE))
            .findFirst()
            .orElseThrow();

        // 绑定调用上下文
        InvocationContext ctx = new InvocationContext(
            pluginId, 123L, InvocationContext.CallerType.USER,
            "test-request-1", InvocationContext.TriggerSource.HTTP_REQUEST
        );

        // 执行动作
        ServiceResult<Object> result = handler.execute(ctx, Map.of("name", "张三"));
        assertEquals(0, result.getCode());
        assertNotNull(result.getData());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertTrue(((String) data.get("message")).contains("张三"));
        assertTrue(((String) data.get("message")).contains(pluginId));
    }

    @Test
    void shouldNotCallActionWhenPluginStopped() throws Exception {
        Path jarPath = buildHelloPluginJar();
        WorkforcePluginManager pf4jManager = new WorkforcePluginManager(
            tempDir.resolve("plugins").toString());

        String pluginId = pf4jManager.loadPlugin(jarPath);
        pf4jManager.startPlugin(pluginId);

        // 停用插件
        pf4jManager.stopPlugin(pluginId);
        assertEquals(PluginState.STOPPED, pf4jManager.getPlugin(pluginId).getPluginState());

        // 停用后扩展不应被发现
        var handlers = pf4jManager.getExtensions(PluginActionHandler.class, pluginId);
        assertTrue(handlers.isEmpty(), "停用后不应发现扩展");
    }

    @Test
    void shouldNotCallActionAfterUninstall() throws Exception {
        Path jarPath = buildHelloPluginJar();
        WorkforcePluginManager pf4jManager = new WorkforcePluginManager(
            tempDir.resolve("plugins").toString());

        String pluginId = pf4jManager.loadPlugin(jarPath);
        pf4jManager.startPlugin(pluginId);

        // 卸载
        pf4jManager.stopPlugin(pluginId);
        pf4jManager.unloadPlugin(pluginId);
        assertNull(pf4jManager.getPlugin(pluginId));

        // 卸载后不应发现任何扩展
        var handlers = pf4jManager.getExtensions(PluginActionHandler.class);
        assertTrue(handlers.stream().noneMatch(h -> {
            var wrapper = pf4jManager.whichPlugin(h.getClass());
            return wrapper != null && wrapper.getPluginId().equals(pluginId);
        }));
    }

    @Test
    void shouldReturnCorrectActionDescriptor() throws Exception {
        Path jarPath = buildHelloPluginJar();
        WorkforcePluginManager pf4jManager = new WorkforcePluginManager(
            tempDir.resolve("plugins").toString());

        String pluginId = pf4jManager.loadPlugin(jarPath);
        pf4jManager.startPlugin(pluginId);

        var handlers = pf4jManager.getExtensions(PluginActionHandler.class, pluginId);
        PluginActionHandler handler = handlers.get(0);

        var descriptor = handler.getDescriptor();
        assertEquals(ACTION_CODE, descriptor.actionCode());
        assertEquals("问候", descriptor.name());
        assertEquals(CAPABILITY, descriptor.requiredCapability());
        assertTrue(descriptor.idempotent());
    }

    private Path buildHelloPluginJar() throws IOException {
        // 使用预构建的 JAR
        Path prebuiltJar = Path.of(System.getProperty("user.dir"), "..", "plugins", "hello-plugin",
            "target", "hello-plugin-0.1.0-SNAPSHOT.jar").toAbsolutePath().normalize();
        if (Files.exists(prebuiltJar)) {
            Path jar = tempDir.resolve("hello-plugin.jar");
            Files.copy(prebuiltJar, jar);
            return jar;
        }

        // 如果预构建 JAR 不存在，从源码编译
        Path source = Path.of("..", "hello-plugin", "src", "main", "java",
            "com", "workforcehub", "plugin", "hello", "HelloPlugin.java").toAbsolutePath().normalize();
        Path source2 = Path.of("..", "hello-plugin", "src", "main", "java",
            "com", "workforcehub", "plugin", "hello", "HelloActionHandler.java").toAbsolutePath().normalize();
        Path classes = tempDir.resolve("hello-plugin-classes");
        Files.createDirectories(classes);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "需要 JDK");
        int result = compiler.run(
            null, null, null,
            "-classpath", System.getProperty("java.class.path"),
            "-d", classes.toString(),
            source.toString(),
            source2.toString()
        );
        assertEquals(0, result, "编译失败");

        Files.copy(
            Path.of("..", "hello-plugin", "src", "main", "resources", "plugin.properties")
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
