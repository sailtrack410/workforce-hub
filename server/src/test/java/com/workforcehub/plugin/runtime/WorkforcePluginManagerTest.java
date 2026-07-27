package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.HostService;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.PluginContext;
import com.workforcehub.plugin.sdk.RequiresCapability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.Plugin;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginFactory;
import org.pf4j.PluginWrapper;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkforcePluginManagerTest {

    interface StubService extends HostService {
        @RequiresCapability("STUB_CAP")
        String hello(InvocationContext ctx);
    }

    public static class ContextProbePlugin extends Plugin {
        private final PluginContext context;

        public ContextProbePlugin(PluginWrapper wrapper, PluginContext context) {
            super(wrapper);
            this.context = context;
        }

        public StubService probeService() {
            return context.getService(StubService.class);
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
    void twoParamConstructorShouldWorkWithEmptyRegistry() {
        PluginPermissionService permService = mock(PluginPermissionService.class);
        WorkforcePluginManager manager = new WorkforcePluginManager(
            tempDir.resolve("plugins").toString(),
            permService
        );

        assertNotNull(manager);
    }
}
