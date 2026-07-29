package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.HostService;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.PluginContext;
import com.workforcehub.plugin.sdk.PluginConfig;
import com.workforcehub.plugin.sdk.RequiresCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultPluginContextTest {

    private PluginPermissionService permissionService;
    private PluginConfig pluginConfig;

    @BeforeEach
    void setUp() {
        permissionService = org.mockito.Mockito.mock(PluginPermissionService.class);
        pluginConfig = key -> java.util.Optional.empty();
    }

    interface TestService extends HostService {
        @RequiresCapability("TEST_CAP")
        String getValue(InvocationContext ctx);
    }

    @Test
    void shouldReturnProxyForRegisteredService() {
        TestService impl = ctx -> "test-value";
        HostServiceRegistry registry = new HostServiceRegistry(List.of(impl), permissionService);
        DefaultPluginContext ctx = new DefaultPluginContext("p1", registry, pluginConfig);

        TestService result = ctx.getService(TestService.class);
        assertNotNull(result);
        assertNotSame(impl, result);
    }

    @Test
    void shouldThrowForUnregisteredService() {
        HostServiceRegistry registry = new HostServiceRegistry(List.of(), permissionService);
        DefaultPluginContext ctx = new DefaultPluginContext("p1", registry, pluginConfig);

        assertThrows(IllegalArgumentException.class,
            () -> ctx.getService(TestService.class));
    }

    @Test
    void shouldRejectHostServiceClass() {
        HostServiceRegistry registry = new HostServiceRegistry(List.of(), permissionService);
        DefaultPluginContext ctx = new DefaultPluginContext("p1", registry, pluginConfig);

        assertThrows(IllegalArgumentException.class,
            () -> ctx.getService(HostService.class));
    }

    @Test
    void shouldRejectConcreteClass() {
        TestService impl = ctx -> "val";
        HostServiceRegistry registry = new HostServiceRegistry(List.of(impl), permissionService);
        DefaultPluginContext ctx = new DefaultPluginContext("p1", registry, pluginConfig);

        assertThrows(IllegalArgumentException.class,
            () -> ctx.getService(impl.getClass()));
    }

    @Test
    void shouldRejectNullType() {
        HostServiceRegistry registry = new HostServiceRegistry(List.of(), permissionService);
        DefaultPluginContext ctx = new DefaultPluginContext("p1", registry, pluginConfig);

        assertThrows(IllegalArgumentException.class,
            () -> ctx.getService(null));
    }

    @Test
    void shouldReturnPluginId() {
        HostServiceRegistry registry = new HostServiceRegistry(List.of(), permissionService);
        DefaultPluginContext ctx = new DefaultPluginContext("my-plugin", registry, pluginConfig);

        assertEquals("my-plugin", ctx.getPluginId());
    }

    @Test
    void shouldReturnBoundPluginConfig() {
        HostServiceRegistry registry = new HostServiceRegistry(List.of(), permissionService);
        DefaultPluginContext ctx = new DefaultPluginContext("p1", registry, pluginConfig);

        assertSame(pluginConfig, ctx.getConfig());
    }

    @Test
    void shouldNotExposeInvocationContext() {
        assertThrows(NoSuchMethodException.class,
            () -> PluginContext.class.getMethod("getInvocationContext"));
    }
}
