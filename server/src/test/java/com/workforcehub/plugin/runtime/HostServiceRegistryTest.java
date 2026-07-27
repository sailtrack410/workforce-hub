package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.HostService;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.RequiresCapability;
import com.workforcehub.plugin.sdk.TestInvocationContextBinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HostServiceRegistryTest {

    private PluginPermissionService permissionService;
    private TestInvocationContextBinder binder;

    @BeforeEach
    void setUp() {
        permissionService = mock(PluginPermissionService.class);
        binder = new TestInvocationContextBinder();
    }

    interface FakeService extends HostService {
        @RequiresCapability("FAKE_CAP")
        String doSomething(InvocationContext ctx);
    }

    interface AnotherService extends HostService {
        @RequiresCapability("ANOTHER_CAP")
        int compute(InvocationContext ctx);
    }

    interface NoAnnotationService extends HostService {
        String bare(InvocationContext ctx);
    }

    interface NoCtxService extends HostService {
        @RequiresCapability("SOME_CAP")
        String noCtx(String param);
    }

    interface MarkerOnly extends HostService {}

    @Test
    void shouldReturnProxyForRegisteredService() {
        FakeService impl = ctx -> "hello";
        HostServiceRegistry registry = new HostServiceRegistry(List.of(impl), permissionService);

        FakeService result = registry.getRequired(FakeService.class, "p1");
        assertNotNull(result);
        assertNotSame(impl, result);
        assertTrue(result instanceof HostService);
    }

    @Test
    void shouldDelegateToImplWhenCapabilityGranted() {
        FakeService impl = ctx -> "delegated";
        HostServiceRegistry registry = new HostServiceRegistry(List.of(impl), permissionService);
        FakeService proxy = registry.getRequired(FakeService.class, "p1");

        InvocationContext ctx = binder.forHttpRequest("p1", 1L,
            InvocationContext.CallerType.USER, "req-1");

        assertEquals("delegated", proxy.doSomething(ctx));
    }

    @Test
    void shouldDenyWhenCapabilityMissing() {
        FakeService impl = ctx -> "should not reach";
        HostServiceRegistry registry = new HostServiceRegistry(List.of(impl), permissionService);
        FakeService proxy = registry.getRequired(FakeService.class, "p1");

        InvocationContext ctx = binder.forHttpRequest("p1", 1L,
            InvocationContext.CallerType.USER, "req-1");
        doThrow(new PluginPermissionService.PermissionDeniedException("插件未被授予能力: FAKE_CAP"))
            .when(permissionService).checkPluginCapability("p1", "FAKE_CAP");

        assertThrows(PluginPermissionService.PermissionDeniedException.class,
            () -> proxy.doSomething(ctx));
    }

    @Test
    void shouldDenyWhenCtxPluginIdMismatch() {
        FakeService delegate = mock(FakeService.class);
        HostServiceRegistry registry = new HostServiceRegistry(List.of(delegate), permissionService);
        FakeService proxy = registry.getRequired(FakeService.class, "p1");

        InvocationContext ctx = binder.forHttpRequest("other-plugin", 1L,
            InvocationContext.CallerType.USER, "req-1");

        PluginPermissionService.PermissionDeniedException ex = assertThrows(
            PluginPermissionService.PermissionDeniedException.class,
            () -> proxy.doSomething(ctx));
        assertTrue(ex.getMessage().contains("pluginId 不匹配"));
        verifyNoInteractions(permissionService);
        verifyNoInteractions(delegate);
    }

    @Test
    void shouldDenyWhenFirstArgIsNull() {
        FakeService impl = ctx -> "should not reach";
        HostServiceRegistry registry = new HostServiceRegistry(List.of(impl), permissionService);
        FakeService proxy = registry.getRequired(FakeService.class, "p1");

        assertThrows(PluginPermissionService.PermissionDeniedException.class,
            () -> proxy.doSomething(null));
        verifyNoInteractions(permissionService);
    }

    @Test
    void shouldRejectUnregisteredService() {
        HostServiceRegistry registry = new HostServiceRegistry(List.of(), permissionService);

        assertThrows(IllegalArgumentException.class,
            () -> registry.getRequired(FakeService.class, "p1"));
    }

    @Test
    void shouldRejectHostServiceClass() {
        FakeService impl = ctx -> "hello";
        HostServiceRegistry registry = new HostServiceRegistry(List.of(impl), permissionService);

        assertThrows(IllegalArgumentException.class,
            () -> registry.getRequired(HostService.class, "p1"));
    }

    @Test
    void shouldRejectConcreteClass() {
        FakeService impl = ctx -> "hello";
        HostServiceRegistry registry = new HostServiceRegistry(List.of(impl), permissionService);

        assertThrows(IllegalArgumentException.class,
            () -> registry.getRequired(impl.getClass(), "p1"));
    }

    @Test
    void shouldFailRegistrationWithoutAnnotation() {
        assertThrows(IllegalStateException.class,
            () -> new HostServiceRegistry(List.of(new NoAnnotationService() {
                @Override
                public String bare(InvocationContext ctx) { return "bare"; }
            }), permissionService));
    }

    @Test
    void shouldFailRegistrationWithoutInvocationContextFirstParam() {
        assertThrows(IllegalStateException.class,
            () -> new HostServiceRegistry(List.of(new NoCtxService() {
                @Override
                public String noCtx(String param) { return param; }
            }), permissionService));
    }

    @Test
    void shouldFailFastOnDuplicateImplementations() {
        FakeService impl1 = ctx -> "a";
        FakeService impl2 = ctx -> "b";

        assertThrows(IllegalStateException.class,
            () -> new HostServiceRegistry(List.of(impl1, impl2), permissionService));
    }

    @Test
    void shouldRejectMarkerOnlyImplementation() {
        assertThrows(IllegalStateException.class,
            () -> new HostServiceRegistry(List.of(new MarkerOnly() {}), permissionService));
    }

    @Test
    void shouldRegisterMultipleDifferentServices() {
        FakeService fake = ctx -> "hello";
        AnotherService another = ctx -> 42;
        HostServiceRegistry registry = new HostServiceRegistry(List.of(fake, another), permissionService);

        assertNotNull(registry.getRequired(FakeService.class, "p1"));
        assertNotNull(registry.getRequired(AnotherService.class, "p1"));
    }

    @Test
    void shouldRejectNullType() {
        HostServiceRegistry registry = new HostServiceRegistry(List.of(), permissionService);

        assertThrows(IllegalArgumentException.class,
            () -> registry.getRequired(null, "p1"));
    }

    @Test
    void shouldRejectNullPluginId() {
        HostServiceRegistry registry = new HostServiceRegistry(List.of(), permissionService);

        assertThrows(IllegalArgumentException.class,
            () -> registry.getRequired(FakeService.class, null));
    }

    @Test
    void shouldRejectBlankPluginId() {
        HostServiceRegistry registry = new HostServiceRegistry(List.of(), permissionService);

        assertThrows(IllegalArgumentException.class,
            () -> registry.getRequired(FakeService.class, "  "));
    }

    @Test
    void shouldRejectNullPermissionService() {
        assertThrows(NullPointerException.class,
            () -> new HostServiceRegistry(List.of(), null));
    }

    @Test
    void cachedProxyShouldDenyAfterCapabilityRevoked() {
        FakeService delegate = mock(FakeService.class);
        when(delegate.doSomething(any())).thenReturn("executed");
        HostServiceRegistry registry = new HostServiceRegistry(List.of(delegate), permissionService);
        FakeService proxy = registry.getRequired(FakeService.class, "p1");

        InvocationContext ctx = binder.forHttpRequest("p1", 1L,
            InvocationContext.CallerType.USER, "req-1");

        // 第一次允许
        assertEquals("executed", proxy.doSomething(ctx));

        // 撤权
        doThrow(new PluginPermissionService.PermissionDeniedException("能力已撤销"))
            .when(permissionService).checkPluginCapability("p1", "FAKE_CAP");

        // 第二次拒绝
        assertThrows(PluginPermissionService.PermissionDeniedException.class,
            () -> proxy.doSomething(ctx));

        // delegate 只被调用一次
        verify(delegate, times(1)).doSomething(ctx);
        verify(permissionService, times(2)).checkPluginCapability("p1", "FAKE_CAP");
    }

    @Test
    void shouldUnwrapBusinessExceptionFromDelegate() {
        FakeService impl = ctx -> { throw new RuntimeException("插件业务异常"); };
        HostServiceRegistry registry = new HostServiceRegistry(List.of(impl), permissionService);
        FakeService proxy = registry.getRequired(FakeService.class, "p1");

        InvocationContext ctx = binder.forHttpRequest("p1", 1L,
            InvocationContext.CallerType.USER, "req-1");

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> proxy.doSomething(ctx));
        assertEquals("插件业务异常", ex.getMessage());
        assertEquals(RuntimeException.class, ex.getClass());
    }
}
