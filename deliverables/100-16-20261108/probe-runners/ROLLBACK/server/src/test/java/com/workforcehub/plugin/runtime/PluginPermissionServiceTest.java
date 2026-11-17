package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.TestInvocationContextBinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginPermissionServiceTest {

    private static final String PLUGIN_ID = "hello-plugin";
    private static final String ACTION_CODE = "hello.greet";
    private static final String CAPABILITY = "PLUGIN_HELLO";

    private JdbcTemplate jdbc;
    private PluginPermissionService service;
    private TestInvocationContextBinder contextBinder;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new PluginPermissionService(jdbc);
        contextBinder = new TestInvocationContextBinder();

        when(jdbc.query(
            startsWith("SELECT capability_code"),
            any(RowMapper.class),
            eq(PLUGIN_ID),
            eq(ACTION_CODE)
        )).thenReturn(List.of(CAPABILITY));
        when(jdbc.queryForObject(
            startsWith("SELECT COUNT(*) FROM sys_plugin_capability"),
            eq(Integer.class),
            eq(PLUGIN_ID),
            eq(CAPABILITY)
        )).thenReturn(1);
    }

    @Test
    void shouldAllowAuthorizedAdmin() {
        when(jdbc.queryForObject(
            startsWith("SELECT COUNT(*) FROM admin_user_role"),
            eq(Integer.class),
            eq(42L),
            eq("ADMIN_PLUGIN_CALL")
        )).thenReturn(1);
        InvocationContext context = contextBinder.forHttpRequest(
            PLUGIN_ID, 42L, InvocationContext.CallerType.ADMIN, "request-1");

        assertDoesNotThrow(() -> service.checkPermission(context, PLUGIN_ID, ACTION_CODE));
    }

    @Test
    void shouldDenyEmployeeWithoutExplicitPolicy() {
        InvocationContext context = contextBinder.forHttpRequest(
            PLUGIN_ID, 42L, InvocationContext.CallerType.USER, "request-1");

        assertThrows(
            PluginPermissionService.PermissionDeniedException.class,
            () -> service.checkPermission(context, PLUGIN_ID, ACTION_CODE)
        );
    }

    @Test
    void shouldAllowHostBoundPluginCaller() {
        InvocationContext context = contextBinder.forPluginStartup(PLUGIN_ID);

        assertDoesNotThrow(() -> service.checkPermission(context, PLUGIN_ID, ACTION_CODE));
    }

    @Test
    void shouldDenyActionMissingFromHostConfiguration() {
        when(jdbc.query(
            startsWith("SELECT capability_code"),
            any(RowMapper.class),
            eq(PLUGIN_ID),
            eq("missing.action")
        )).thenReturn(List.of());
        InvocationContext context = contextBinder.forPluginStartup(PLUGIN_ID);

        assertThrows(
            PluginPermissionService.PermissionDeniedException.class,
            () -> service.checkPermission(context, PLUGIN_ID, "missing.action")
        );
    }

    @Test
    void shouldDenyPluginWithoutCapabilityGrant() {
        when(jdbc.queryForObject(
            startsWith("SELECT COUNT(*) FROM sys_plugin_capability"),
            eq(Integer.class),
            eq(PLUGIN_ID),
            eq(CAPABILITY)
        )).thenReturn(0);
        InvocationContext context = contextBinder.forPluginStartup(PLUGIN_ID);

        assertThrows(
            PluginPermissionService.PermissionDeniedException.class,
            () -> service.checkPermission(context, PLUGIN_ID, ACTION_CODE)
        );
    }

    @Test
    void checkPluginCapabilityShouldAllowWhenGranted() {
        assertDoesNotThrow(() -> service.checkPluginCapability(PLUGIN_ID, CAPABILITY));
    }

    @Test
    void checkPluginCapabilityShouldDenyWhenNotGranted() {
        when(jdbc.queryForObject(
            startsWith("SELECT COUNT(*) FROM sys_plugin_capability"),
            eq(Integer.class),
            eq(PLUGIN_ID),
            eq("UNGRANTED_CAP")
        )).thenReturn(0);

        assertThrows(
            PluginPermissionService.PermissionDeniedException.class,
            () -> service.checkPluginCapability(PLUGIN_ID, "UNGRANTED_CAP")
        );
    }

    @Test
    void checkPluginCapabilityShouldDenyNullCapability() {
        assertThrows(
            PluginPermissionService.PermissionDeniedException.class,
            () -> service.checkPluginCapability(PLUGIN_ID, null)
        );
    }

    @Test
    void checkPluginCapabilityShouldDenyBlankCapability() {
        assertThrows(
            PluginPermissionService.PermissionDeniedException.class,
            () -> service.checkPluginCapability(PLUGIN_ID, "  ")
        );
    }
}
