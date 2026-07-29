package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginConfigRepository;
import com.workforcehub.plugin.repository.PluginConfigRow;
import com.workforcehub.plugin.sdk.HostService;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.PluginContext;
import com.workforcehub.plugin.sdk.RequiresCapability;
import com.workforcehub.plugin.sdk.WorkforcePlugin;
import jakarta.annotation.PreDestroy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.Plugin;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginFactory;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkforcePluginManagerTest {

    interface StubService extends HostService {
        @RequiresCapability("STUB_CAP")
        String hello(InvocationContext ctx);
    }

    public static class ContextProbePlugin extends WorkforcePlugin {
        private final PluginContext context;

        public ContextProbePlugin(PluginWrapper wrapper, PluginContext context) {
            super(wrapper, context);
            this.context = context;
        }

        public StubService probeService() {
            return context.getService(StubService.class);
        }

        public Optional<String> probeConfig(String key) {
            return context.getConfig().get(key);
        }
    }

    public static class InvalidPlugin extends Plugin {
        private static final AtomicBoolean constructorCalled = new AtomicBoolean();

        public InvalidPlugin(PluginWrapper wrapper, PluginContext context) {
            super(wrapper);
            constructorCalled.set(true);
        }
    }

    @TempDir
    Path tempDir;

    @Test
    void threeParamConstructorShouldPassHostServiceToPluginContext() {
        StubService impl = ctx -> "world";
        PluginPermissionService permService = mock(PluginPermissionService.class);
        WorkforcePluginManager manager = new WorkforcePluginManager(
            tempDir.resolve("plugins").toString(),
            List.of(impl),
            permService
        );

        PluginFactory factory = manager.createPluginFactory();

        PluginDescriptor descriptor = mock(PluginDescriptor.class);
        when(descriptor.getPluginClass()).thenReturn(ContextProbePlugin.class.getName());

        PluginWrapper wrapper = mock(PluginWrapper.class);
        when(wrapper.getDescriptor()).thenReturn(descriptor);
        when(wrapper.getPluginClassLoader()).thenReturn(getClass().getClassLoader());
        when(wrapper.getPluginId()).thenReturn("probe-plugin");

        Plugin plugin = factory.create(wrapper);
        assertSame(ContextProbePlugin.class, plugin.getClass());

        ContextProbePlugin probe = (ContextProbePlugin) plugin;
        StubService service = probe.probeService();
        assertNotNull(service);
        assertNotSame(impl, service); // 应返回代理，不是原始 impl
    }

    @Test
    void factoryShouldRejectPluginThatBypassesWorkforceLifecycle() {
        InvalidPlugin.constructorCalled.set(false);
        WorkforcePluginManager manager = new WorkforcePluginManager(
            tempDir.resolve("plugins").toString(),
            mock(PluginPermissionService.class)
        );
        PluginFactory factory = manager.createPluginFactory();

        PluginDescriptor descriptor = mock(PluginDescriptor.class);
        when(descriptor.getPluginClass()).thenReturn(InvalidPlugin.class.getName());

        PluginWrapper wrapper = mock(PluginWrapper.class);
        when(wrapper.getDescriptor()).thenReturn(descriptor);
        when(wrapper.getPluginClassLoader()).thenReturn(getClass().getClassLoader());
        when(wrapper.getPluginId()).thenReturn("invalid-plugin");

        assertThrows(PluginRuntimeException.class, () -> factory.create(wrapper));
        assertFalse(InvalidPlugin.constructorCalled.get());
    }

    @Test
    void springConstructorShouldBindPluginConfigNamespace() {
        PluginPermissionService permService = mock(PluginPermissionService.class);
        PluginConfigRepository configRepository = mock(PluginConfigRepository.class);
        when(configRepository.find("probe-plugin", "shared.key")).thenReturn(Optional.of(
            new PluginConfigRow(
                "probe-plugin", "shared.key", "bound-value", "STRING", null, null,
                false, false, false, 1, LocalDateTime.of(2026, 7, 29, 0, 0)
            )
        ));
        PluginConfigCipher cipher = new PluginConfigCipher(
            Base64.getEncoder().encodeToString(new byte[32]));
        WorkforcePluginManager manager = new WorkforcePluginManager(
            tempDir.resolve("plugins").toString(),
            List.of(),
            permService,
            configRepository,
            cipher
        );
        PluginDescriptor descriptor = mock(PluginDescriptor.class);
        when(descriptor.getPluginClass()).thenReturn(ContextProbePlugin.class.getName());
        PluginWrapper wrapper = mock(PluginWrapper.class);
        when(wrapper.getDescriptor()).thenReturn(descriptor);
        when(wrapper.getPluginClassLoader()).thenReturn(getClass().getClassLoader());
        when(wrapper.getPluginId()).thenReturn("probe-plugin");

        ContextProbePlugin plugin = (ContextProbePlugin) manager.createPluginFactory().create(wrapper);

        assertEquals(Optional.of("bound-value"), plugin.probeConfig("shared.key"));
        verify(configRepository).find("probe-plugin", "shared.key");
    }

    @Test
    void closeShouldBeManagedBySpringLifecycle() throws Exception {
        assertNotNull(
            WorkforcePluginManager.class.getMethod("close").getAnnotation(PreDestroy.class)
        );
    }

    @Test
    void closeShouldNotUnloadPluginWhileCallbackIsActive() {
        String pluginId = "active-callback-plugin";
        PluginLifecycleGuard lifecycleGuard = new PluginLifecycleGuard(0);
        lifecycleGuard.enable(pluginId);
        assertTrue(lifecycleGuard.tryAcquire(pluginId));
        WorkforcePluginManager manager = spy(new WorkforcePluginManager(
            tempDir.resolve("active-callback-plugins").toString(),
            List.of(),
            mock(PluginPermissionService.class),
            lifecycleGuard
        ));
        PluginWrapper wrapper = mock(PluginWrapper.class);
        when(wrapper.getPluginId()).thenReturn(pluginId);
        when(wrapper.getPluginState()).thenReturn(PluginState.STARTED);
        doReturn(List.of(wrapper)).when(manager).getPlugins();
        doReturn(wrapper).when(manager).getPlugin(pluginId);

        assertThrows(PluginRuntimeException.class, manager::close);

        verify(manager, never()).unloadPlugin(pluginId);
        assertEquals(1, lifecycleGuard.getActiveCount(pluginId));
        assertFalse(lifecycleGuard.tryAcquire(pluginId));
        lifecycleGuard.release(pluginId);
    }

    @Test
    void closeShouldNotCascadeUnloadBeforeAllPluginsDrain() {
        String dependencyId = "dependency-plugin";
        String dependentId = "dependent-plugin";
        PluginLifecycleGuard lifecycleGuard = new PluginLifecycleGuard(0);
        lifecycleGuard.enable(dependencyId);
        lifecycleGuard.enable(dependentId);
        assertTrue(lifecycleGuard.tryAcquire(dependentId));
        WorkforcePluginManager manager = spy(new WorkforcePluginManager(
            tempDir.resolve("dependent-callback-plugins").toString(),
            List.of(),
            mock(PluginPermissionService.class),
            lifecycleGuard
        ));
        PluginWrapper dependency = mock(PluginWrapper.class);
        PluginWrapper dependent = mock(PluginWrapper.class);
        when(dependency.getPluginId()).thenReturn(dependencyId);
        when(dependent.getPluginId()).thenReturn(dependentId);
        doReturn(List.of(dependency, dependent)).when(manager).getPlugins();
        doReturn(dependency).when(manager).getPlugin(dependencyId);
        doReturn(dependent).when(manager).getPlugin(dependentId);

        assertThrows(PluginRuntimeException.class, manager::close);

        verify(manager, never()).unloadPlugin(anyString());
        assertFalse(lifecycleGuard.tryAcquire(dependencyId));
        assertFalse(lifecycleGuard.tryAcquire(dependentId));
        assertEquals(1, lifecycleGuard.getActiveCount(dependentId));
        lifecycleGuard.release(dependentId);
    }

    @Test
    void twoParamConstructorShouldWorkWithEmptyRegistry() {
        PluginPermissionService permService = mock(PluginPermissionService.class);
        WorkforcePluginManager manager = new WorkforcePluginManager(
            tempDir.resolve("plugins").toString(),
            permService
        );

        assertNotNull(manager);
    }
}
