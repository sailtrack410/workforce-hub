package com.workforcehub.plugin;

import com.workforcehub.plugin.runtime.PluginPermissionService;
import com.workforcehub.plugin.runtime.WorkforcePluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PluginResourceLifecycleTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReleaseThreadThreadLocalAndDriverBeforeClassLoaderCollection() throws Exception {
        WorkforcePluginManager pluginManager = new WorkforcePluginManager(
            tempDir.resolve("plugins").toString(), mock(PluginPermissionService.class));
        ReferenceQueue<ClassLoader> referenceQueue = new ReferenceQueue<>();
        AtomicReference<Thread> hostThread = new AtomicReference<>();
        ExecutorService hostExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "host-threadlocal-probe");
            hostThread.set(thread);
            return thread;
        });

        try {
            WeakReference<ClassLoader> classLoaderReference = exerciseResourceLifecycle(
                pluginManager,
                buildResourcePluginJar(),
                hostExecutor,
                referenceQueue
            );
            assertNotNull(hostThread.get());
            assertTrue(hostThread.get().isAlive(), "宿主线程必须保持存活以验证 ThreadLocal 已清理");
            assertClassLoaderCollected(classLoaderReference, referenceQueue);
        } finally {
            try {
                hostExecutor.shutdownNow();
                assertTrue(hostExecutor.awaitTermination(5, TimeUnit.SECONDS));
            } finally {
                pluginManager.close();
                Reference.reachabilityFence(pluginManager);
            }
        }
    }

    @Test
    void shouldReleaseResourcesWhenPluginStartFails() throws Exception {
        WorkforcePluginManager pluginManager = new WorkforcePluginManager(
            tempDir.resolve("failed-start-plugins").toString(), mock(PluginPermissionService.class));
        try {
            String pluginId = pluginManager.loadPlugin(buildResourcePluginJar());
            Object plugin = pluginManager.getPlugin(pluginId).getPlugin();
            invoke(plugin, "failNextStart");

            assertEquals(PluginState.FAILED, pluginManager.startPlugin(pluginId));
            assertFalse(invokeBoolean(plugin, "isWorkerAlive"));
            assertTrue(invokeBoolean(plugin, "isWorkerThreadLocalCleared"));
            assertTrue(invokeBoolean(plugin, "isWorkerContextClassLoaderReleased"));
            assertFalse(invokeBoolean(plugin, "isDriverRegistered"));
            assertTrue(invokeBoolean(plugin, "isDriverActionCalled"));
            assertTrue(pluginManager.unloadPlugin(pluginId));
        } finally {
            closeManager(pluginManager);
        }
    }

    @Test
    void shouldUnloadFailedWrapperWhenPluginWasNeverInstantiated() throws Exception {
        WorkforcePluginManager pluginManager = new WorkforcePluginManager(
            tempDir.resolve("missing-main-class-plugins").toString(),
            mock(PluginPermissionService.class)
        );
        try {
            String pluginId = pluginManager.loadPlugin(buildMissingMainClassPluginJar());
            PluginClassLoader classLoader = assertInstanceOf(
                PluginClassLoader.class,
                pluginManager.getPluginClassLoader(pluginId)
            );

            assertEquals(PluginState.FAILED, pluginManager.startPlugin(pluginId));
            assertTrue(pluginManager.unloadPlugin(pluginId));
            assertTrue(classLoader.isClosed());
            assertNull(pluginManager.getPlugin(pluginId));
        } finally {
            closeManager(pluginManager);
        }
    }

    @Test
    void shouldBlockRestartAndUnloadUntilFailedCleanupRetriesSuccessfully() throws Exception {
        WorkforcePluginManager pluginManager = new WorkforcePluginManager(
            tempDir.resolve("retry-cleanup-plugins").toString(), mock(PluginPermissionService.class));
        try {
            String pluginId = pluginManager.loadPlugin(buildResourcePluginJar());
            assertEquals(PluginState.STARTED, pluginManager.startPlugin(pluginId));
            PluginWrapper wrapper = pluginManager.getPlugin(pluginId);
            Object plugin = wrapper.getPlugin();
            PluginClassLoader classLoader = assertInstanceOf(
                PluginClassLoader.class,
                pluginManager.getPluginClassLoader(pluginId)
            );
            invoke(plugin, "failNextDriverCleanup");

            assertFalse(pluginManager.unloadPlugin(pluginId));
            assertEquals(PluginState.FAILED, wrapper.getPluginState());
            assertNotNull(pluginManager.getPlugin(pluginId));
            assertFalse(classLoader.isClosed());
            assertFalse(invokeBoolean(plugin, "isWorkerAlive"));
            assertTrue(invokeBoolean(plugin, "isDriverRegistered"));

            assertEquals(PluginState.FAILED, pluginManager.startPlugin(pluginId));
            assertTrue(invokeBoolean(plugin, "isDriverRegistered"));

            assertTrue(pluginManager.unloadPlugin(pluginId));
            assertFalse(invokeBoolean(plugin, "isDriverRegistered"));
            assertTrue(invokeBoolean(plugin, "isDriverActionCalled"));
            assertTrue(classLoader.isClosed());
            assertNull(pluginManager.getPlugin(pluginId));
        } finally {
            closeManager(pluginManager);
        }
    }

    @Test
    void closeShouldContinuePastCleanupErrorAndUnloadUnresolvedPlugins() throws Exception {
        WorkforcePluginManager pluginManager = new WorkforcePluginManager(
            tempDir.resolve("manager-close-plugins").toString(), mock(PluginPermissionService.class));
        try {
            String healthyId = pluginManager.loadPlugin(buildResourcePluginJar("resource-healthy", null));
            String failingId = pluginManager.loadPlugin(buildResourcePluginJar("resource-failing", null));
            assertEquals(PluginState.STARTED, pluginManager.startPlugin(healthyId));
            assertEquals(PluginState.STARTED, pluginManager.startPlugin(failingId));
            Object failingPlugin = pluginManager.getPlugin(failingId).getPlugin();
            PluginClassLoader failingClassLoader = assertInstanceOf(
                PluginClassLoader.class,
                pluginManager.getPluginClassLoader(failingId)
            );
            invoke(failingPlugin, "failNextDriverCleanupWithError");

            String unresolvedId = "resource-unresolved";
            assertThrows(
                RuntimeException.class,
                () -> pluginManager.loadPlugin(buildResourcePluginJar(
                    unresolvedId,
                    "missing-required-plugin"
                ))
            );
            assertNotNull(pluginManager.getPlugin(unresolvedId));

            PluginRuntimeException thrown = assertThrows(PluginRuntimeException.class, pluginManager::close);

            assertTrue(thrown.getMessage().contains("关闭失败"));
            assertNull(pluginManager.getPlugin(healthyId));
            assertNull(pluginManager.getPlugin(unresolvedId));
            assertNotNull(pluginManager.getPlugin(failingId));
            assertFalse(failingClassLoader.isClosed());
            assertTrue(invokeBoolean(failingPlugin, "isDriverRegistered"));

            assertDoesNotThrow(pluginManager::close);
            assertNull(pluginManager.getPlugin(failingId));
            assertTrue(failingClassLoader.isClosed());
            assertFalse(invokeBoolean(failingPlugin, "isDriverRegistered"));
        } finally {
            closeManager(pluginManager);
        }
    }

    @Test
    void stopPluginsShouldContinuePastCleanupErrorAndRetryFailedPlugin() throws Exception {
        WorkforcePluginManager pluginManager = new WorkforcePluginManager(
            tempDir.resolve("manager-stop-plugins").toString(), mock(PluginPermissionService.class));
        try {
            String healthyId = pluginManager.loadPlugin(buildResourcePluginJar("stop-healthy", null));
            String failingId = pluginManager.loadPlugin(buildResourcePluginJar("stop-failing", null));
            assertEquals(PluginState.STARTED, pluginManager.startPlugin(healthyId));
            assertEquals(PluginState.STARTED, pluginManager.startPlugin(failingId));
            Object failingPlugin = pluginManager.getPlugin(failingId).getPlugin();
            invoke(failingPlugin, "failNextDriverCleanupWithError");

            assertThrows(PluginRuntimeException.class, pluginManager::stopPlugins);
            assertEquals(PluginState.STOPPED, pluginManager.getPlugin(healthyId).getPluginState());
            assertEquals(PluginState.FAILED, pluginManager.getPlugin(failingId).getPluginState());
            assertTrue(invokeBoolean(failingPlugin, "isDriverRegistered"));

            assertDoesNotThrow(pluginManager::stopPlugins);
            assertEquals(PluginState.STOPPED, pluginManager.getPlugin(failingId).getPluginState());
            assertFalse(invokeBoolean(failingPlugin, "isDriverRegistered"));
        } finally {
            closeManager(pluginManager);
        }
    }

    @Test
    void stopPluginsShouldRetryCleanupAfterStartFailure() throws Exception {
        WorkforcePluginManager pluginManager = new WorkforcePluginManager(
            tempDir.resolve("manager-failed-start-plugins").toString(),
            mock(PluginPermissionService.class)
        );
        try {
            String pluginId = pluginManager.loadPlugin(buildResourcePluginJar(
                "failed-start-cleanup",
                null
            ));
            PluginWrapper wrapper = pluginManager.getPlugin(pluginId);
            Object plugin = wrapper.getPlugin();
            invoke(plugin, "failNextStart");
            invoke(plugin, "failNextDriverCleanup");

            assertEquals(PluginState.FAILED, pluginManager.startPlugin(pluginId));
            assertTrue(invokeBoolean(plugin, "isDriverRegistered"));

            assertDoesNotThrow(pluginManager::stopPlugins);
            assertEquals(PluginState.STOPPED, wrapper.getPluginState());
            assertFalse(invokeBoolean(plugin, "isDriverRegistered"));
        } finally {
            closeManager(pluginManager);
        }
    }

    private void closeManager(WorkforcePluginManager pluginManager) {
        try {
            pluginManager.close();
        } catch (RuntimeException firstFailure) {
            try {
                pluginManager.close();
            } catch (RuntimeException secondFailure) {
                firstFailure.addSuppressed(secondFailure);
                throw firstFailure;
            }
        }
    }

    private WeakReference<ClassLoader> exerciseResourceLifecycle(
            WorkforcePluginManager pluginManager,
            Path pluginJar,
            ExecutorService hostExecutor,
            ReferenceQueue<ClassLoader> referenceQueue) throws Exception {
        String pluginId = pluginManager.loadPlugin(pluginJar);
        assertEquals(PluginState.STARTED, pluginManager.startPlugin(pluginId));

        PluginWrapper wrapper = pluginManager.getPlugin(pluginId);
        assertNotNull(wrapper);
        Object plugin = wrapper.getPlugin();
        PluginClassLoader classLoader = assertInstanceOf(
            PluginClassLoader.class,
            pluginManager.getPluginClassLoader(pluginId)
        );

        assertTrue(invokeBoolean(plugin, "isWorkerAlive"));
        assertTrue(invokeBoolean(plugin, "isWorkerThreadLocalSet"));
        assertTrue(invokeBoolean(plugin, "isDriverRegistered"));
        Driver driver = (Driver) invoke(plugin, "getRegisteredDriver");
        assertSame(classLoader, driver.getClass().getClassLoader());
        assertFalse(DriverManager.drivers().anyMatch(candidate -> candidate == driver));
        assertThrows(SecurityException.class, () -> DriverManager.deregisterDriver(driver));
        assertTrue(invokeBoolean(plugin, "isDriverRegistered"));

        hostExecutor.submit(() -> invokeUnchecked(plugin, "useHostThreadLocal")).get(5, TimeUnit.SECONDS);
        assertTrue(invokeBoolean(plugin, "isHostThreadLocalObserved"));
        assertTrue(invokeBoolean(plugin, "isHostThreadLocalCleared"));

        assertEquals(PluginState.STOPPED, pluginManager.stopPlugin(pluginId));
        assertResourcesClosed(plugin);

        assertEquals(PluginState.STARTED, pluginManager.startPlugin(pluginId));
        assertTrue(invokeBoolean(plugin, "isWorkerAlive"));
        assertTrue(invokeBoolean(plugin, "isDriverRegistered"));

        assertTrue(pluginManager.unloadPlugin(pluginId));
        assertResourcesClosed(plugin);
        assertTrue(classLoader.isClosed());
        assertNull(pluginManager.getPlugin(pluginId));
        assertNull(pluginManager.getPluginClassLoader(pluginId));
        return new WeakReference<>(classLoader, referenceQueue);
    }

    private void assertResourcesClosed(Object plugin) throws Exception {
        assertFalse(invokeBoolean(plugin, "isWorkerAlive"));
        assertTrue(invokeBoolean(plugin, "isWorkerThreadLocalCleared"));
        assertTrue(invokeBoolean(plugin, "isWorkerContextClassLoaderReleased"));
        assertFalse(invokeBoolean(plugin, "isDriverRegistered"));
        assertTrue(invokeBoolean(plugin, "isDriverActionCalled"));
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
            "资源清理并卸载后 ClassLoader 应在 5 秒内进入 ReferenceQueue");
        assertNull(classLoaderReference.get());
    }

    private Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        try {
            return method.invoke(target);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    private boolean invokeBoolean(Object target, String methodName) throws Exception {
        return (Boolean) invoke(target, methodName);
    }

    private void invokeUnchecked(Object target, String methodName) {
        try {
            invoke(target, methodName);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private Path buildResourcePluginJar() throws IOException {
        return buildResourcePluginJar("resource-probe-plugin", null);
    }

    private Path buildMissingMainClassPluginJar() throws IOException {
        Path jar = tempDir.resolve("missing-main-class.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("plugin.properties"));
            output.write("""
                plugin.id=missing-main-class
                plugin.class=com.workforcehub.plugin.missing.MissingPlugin
                plugin.version=0.1.0
                plugin.provider=workforce-hub
                plugin.description=Missing main class probe
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }

    private Path buildResourcePluginJar(String pluginId, String dependencies) throws IOException {
        Path sourceRoot = tempDir.resolve(pluginId + "-src");
        Path sourceFile = sourceRoot.resolve(
            "com/workforcehub/plugin/resourceprobe/ResourceProbePlugin.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, RESOURCE_PLUGIN_SOURCE);

        Path classes = tempDir.resolve(pluginId + "-classes");
        Files.createDirectories(classes);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "资源生命周期测试需要 JDK，不能只使用 JRE");
        int result = compiler.run(
            null,
            null,
            null,
            "-classpath",
            System.getProperty("java.class.path"),
            "-d",
            classes.toString(),
            sourceFile.toString()
        );
        assertEquals(0, result, "资源探针插件源码编译失败");

        Files.writeString(classes.resolve("plugin.properties"), """
            plugin.id=%s
            plugin.class=com.workforcehub.plugin.resourceprobe.ResourceProbePlugin
            plugin.version=0.1.0
            plugin.provider=workforce-hub
            plugin.description=Plugin resource lifecycle probe
            plugin.dependencies=%s
            """.formatted(pluginId, dependencies == null ? "" : dependencies));

        Path jar = tempDir.resolve(pluginId + ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar));
             var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList()) {
                String entryName = classes.relativize(file).toString().replace('\\', '/');
                output.putNextEntry(new JarEntry(entryName));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
        return jar;
    }

    private static final String RESOURCE_PLUGIN_SOURCE = """
        package com.workforcehub.plugin.resourceprobe;

        import com.workforcehub.plugin.sdk.PluginContext;
        import com.workforcehub.plugin.sdk.WorkforcePlugin;
        import org.pf4j.PluginWrapper;

        import java.sql.Connection;
        import java.sql.Driver;
        import java.sql.DriverManager;
        import java.sql.DriverPropertyInfo;
        import java.sql.SQLException;
        import java.util.Properties;
        import java.util.concurrent.CountDownLatch;
        import java.util.concurrent.TimeUnit;
        import java.util.logging.Logger;

        public final class ResourceProbePlugin extends WorkforcePlugin {

            private final ThreadLocal<Object> hostThreadLocal = new ThreadLocal<>();
            private volatile boolean running;
            private volatile boolean workerThreadLocalSet;
            private volatile boolean workerThreadLocalCleared;
            private volatile boolean workerContextClassLoaderReleased;
            private volatile boolean hostThreadLocalObserved;
            private volatile boolean hostThreadLocalCleared;
            private volatile boolean driverActionCalled;
            private volatile boolean failNextStart;
            private volatile boolean failNextDriverCleanup;
            private volatile boolean failNextDriverCleanupWithError;
            private Thread worker;
            private Driver driver;

            public ResourceProbePlugin(PluginWrapper wrapper, PluginContext context) {
                super(wrapper, context);
            }

            @Override
            protected synchronized void onStart() throws Exception {
                workerThreadLocalSet = false;
                workerThreadLocalCleared = false;
                workerContextClassLoaderReleased = false;
                driverActionCalled = false;

                driver = new ProbeDriver();
                DriverManager.registerDriver(driver, () -> driverActionCalled = true);
                registerCleanup(this::closeDriver);

                running = true;
                CountDownLatch started = new CountDownLatch(1);
                worker = new Thread(() -> runWorker(started), "resource-probe-worker");
                worker.setDaemon(true);
                worker.setContextClassLoader(getClass().getClassLoader());
                registerCleanup(this::closeWorker);
                worker.start();
                if (!started.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("worker start timeout");
                }
                if (failNextStart) {
                    failNextStart = false;
                    throw new IllegalStateException("requested start failure");
                }
            }

            public void failNextStart() {
                failNextStart = true;
            }

            public void failNextDriverCleanup() {
                failNextDriverCleanup = true;
            }

            public void failNextDriverCleanupWithError() {
                failNextDriverCleanupWithError = true;
            }

            public void useHostThreadLocal() {
                hostThreadLocal.set(new Marker());
                try {
                    hostThreadLocalObserved = hostThreadLocal.get() != null;
                } finally {
                    hostThreadLocal.remove();
                    hostThreadLocalCleared = true;
                }
            }

            public boolean isWorkerAlive() {
                return worker != null && worker.isAlive();
            }

            public boolean isWorkerThreadLocalSet() {
                return workerThreadLocalSet;
            }

            public boolean isWorkerThreadLocalCleared() {
                return workerThreadLocalCleared;
            }

            public boolean isWorkerContextClassLoaderReleased() {
                return workerContextClassLoaderReleased;
            }

            public boolean isHostThreadLocalObserved() {
                return hostThreadLocalObserved;
            }

            public boolean isHostThreadLocalCleared() {
                return hostThreadLocalCleared;
            }

            public boolean isDriverRegistered() {
                Driver current = driver;
                return current != null && DriverManager.drivers().anyMatch(candidate -> candidate == current);
            }

            public boolean isDriverActionCalled() {
                return driverActionCalled;
            }

            public Driver getRegisteredDriver() {
                return driver;
            }

            private void runWorker(CountDownLatch started) {
                ThreadLocal<Object> workerThreadLocal = new ThreadLocal<>();
                workerThreadLocal.set(new Marker());
                workerThreadLocalSet = workerThreadLocal.get() != null;
                started.countDown();
                try {
                    while (running) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {
                            if (!running) {
                                break;
                            }
                        }
                    }
                } finally {
                    workerThreadLocal.remove();
                    workerThreadLocalCleared = true;
                }
            }

            private synchronized void closeWorker() throws InterruptedException {
                running = false;
                Thread current = worker;
                if (current == null) {
                    return;
                }
                current.interrupt();
                current.join(2000);
                if (current.isAlive()) {
                    throw new IllegalStateException("worker stop timeout");
                }
                current.setContextClassLoader(ClassLoader.getSystemClassLoader());
                workerContextClassLoaderReleased =
                    current.getContextClassLoader() != getClass().getClassLoader();
            }

            private synchronized void closeDriver() throws SQLException {
                if (failNextDriverCleanupWithError) {
                    failNextDriverCleanupWithError = false;
                    throw new AssertionError("requested driver cleanup error");
                }
                if (failNextDriverCleanup) {
                    failNextDriverCleanup = false;
                    throw new SQLException("requested driver cleanup failure");
                }
                if (isDriverRegistered()) {
                    DriverManager.deregisterDriver(driver);
                }
            }

            private static final class Marker {
            }

            private static final class ProbeDriver implements Driver {
                @Override
                public Connection connect(String url, Properties info) {
                    return null;
                }

                @Override
                public boolean acceptsURL(String url) {
                    return false;
                }

                @Override
                public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
                    return new DriverPropertyInfo[0];
                }

                @Override
                public int getMajorVersion() {
                    return 1;
                }

                @Override
                public int getMinorVersion() {
                    return 0;
                }

                @Override
                public boolean jdbcCompliant() {
                    return false;
                }

                @Override
                public Logger getParentLogger() {
                    return Logger.getGlobal();
                }
            }
        }
        """;
}
