package com.workforcehub.plugin.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.auth.security.AdminUserDetails;
import com.workforcehub.common.audit.AuditLogWriter;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.plugin.dto.AdminPluginResponse;
import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminPluginManagementServiceTest {

    private static final String PLUGIN_ID = "hello-plugin";
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 7, 30, 10, 1);

    private final PluginManagerService pluginManagerService = mock(PluginManagerService.class);
    private final AuditLogWriter auditLogWriter = mock(AuditLogWriter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdminPluginManagementService service = new AdminPluginManagementService(
        pluginManagerService,
        auditLogWriter,
        objectMapper
    );

    @BeforeEach
    void setCurrentAdmin() {
        AdminUserDetails principal = new AdminUserDetails(
            9001L,
            "plugin.admin",
            "password",
            "插件管理员",
            "ACTIVE",
            List.of(),
            List.of("ADMIN_PLUGIN_MANAGE")
        );
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
            )
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listShouldReturnSafeResponseOnly() {
        PluginRow row = plugin(PluginStatus.ERROR);
        row.setJarPath("/internal/plugins/secret.jar");
        row.setJarSha256("secret-hash");
        row.setSignature(new byte[] {1, 2, 3});
        when(pluginManagerService.listPlugins()).thenReturn(List.of(row));

        List<AdminPluginResponse> result = service.list();

        assertEquals(1, result.size());
        assertEquals(PLUGIN_ID, result.get(0).id());
        assertEquals(PluginStatus.ERROR, result.get(0).status());
        List<String> fields = Arrays.stream(AdminPluginResponse.class.getRecordComponents())
            .map(component -> component.getName())
            .toList();
        assertFalse(fields.contains("jarPath"));
        assertFalse(fields.contains("jarSha256"));
        assertFalse(fields.contains("signature"));
        assertFalse(fields.contains("signerId"));
        assertFalse(fields.contains("integrity"));
        assertFalse(fields.contains("errorMessage"));
    }

    @Test
    void enableShouldChangeStateAndWriteSanitizedAudit() throws Exception {
        PluginRow before = plugin(PluginStatus.INSTALLED);
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(before);
        when(pluginManagerService.enable(PLUGIN_ID)).thenReturn(UPDATED_AT);

        AdminPluginResponse response = service.enable(PLUGIN_ID);

        assertEquals(PluginStatus.ACTIVE, response.status());
        assertEquals(UPDATED_AT, response.updatedAt());
        verify(pluginManagerService).enable(PLUGIN_ID);
        JsonNode detail = capturedAudit("PLUGIN_ENABLED", "SUCCESS");
        assertEquals(PLUGIN_ID, detail.get("pluginId").asText());
        assertEquals("INSTALLED", detail.get("beforeStatus").asText());
        assertEquals("ACTIVE", detail.get("afterStatus").asText());
        assertFalse(detail.has("jarPath"));
        assertFalse(detail.has("signature"));
    }

    @Test
    void repeatedEnableShouldBeIdempotent() {
        PluginRow active = plugin(PluginStatus.ACTIVE);
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(active);

        AdminPluginResponse response = service.enable(PLUGIN_ID);

        assertEquals(PluginStatus.ACTIVE, response.status());
        verify(pluginManagerService, never()).enable(anyString());
        verify(auditLogWriter).write(
            eq("ADMIN"), eq(9001L), eq("PLUGIN_ENABLED"), eq("PLUGIN"), eq(null),
            eq("SUCCESS"), anyString()
        );
    }

    @ParameterizedTest
    @EnumSource(value = PluginStatus.class, names = {"ERROR", "DISABLING", "UNINSTALLED"})
    void enableShouldRejectUnsafeStates(PluginStatus status) {
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(status));

        BizException thrown = assertThrows(BizException.class, () -> service.enable(PLUGIN_ID));

        assertEquals(HttpStatus.CONFLICT, thrown.getHttpStatus());
        verify(pluginManagerService, never()).enable(anyString());
        verify(auditLogWriter).write(
            eq("ADMIN"), eq(9001L), eq("PLUGIN_ENABLED"), eq("PLUGIN"), eq(null),
            eq("BLOCKED"), anyString()
        );
    }

    @Test
    void disableShouldChangeActivePluginToStopped() throws Exception {
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.ACTIVE));
        when(pluginManagerService.disable(PLUGIN_ID)).thenReturn(UPDATED_AT);

        AdminPluginResponse response = service.disable(PLUGIN_ID);

        assertEquals(PluginStatus.STOPPED, response.status());
        verify(pluginManagerService).disable(PLUGIN_ID);
        JsonNode detail = capturedAudit("PLUGIN_DISABLED", "SUCCESS");
        assertEquals("ACTIVE", detail.get("beforeStatus").asText());
        assertEquals("STOPPED", detail.get("afterStatus").asText());
    }

    @Test
    void repeatedDisableShouldBeIdempotent() {
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.STOPPED));

        assertEquals(PluginStatus.STOPPED, service.disable(PLUGIN_ID).status());

        verify(pluginManagerService, never()).disable(anyString());
    }

    @Test
    void uninstallShouldAllowErrorStateRecovery() throws Exception {
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.ERROR));
        when(pluginManagerService.uninstall(PLUGIN_ID)).thenReturn(UPDATED_AT);

        AdminPluginResponse response = service.uninstall(PLUGIN_ID);

        assertEquals(PluginStatus.UNINSTALLED, response.status());
        verify(pluginManagerService).uninstall(PLUGIN_ID);
        JsonNode detail = capturedAudit("PLUGIN_UNINSTALLED", "SUCCESS");
        assertEquals("ERROR", detail.get("beforeStatus").asText());
        assertEquals("UNINSTALLED", detail.get("afterStatus").asText());
    }

    @Test
    void uninstallShouldRetryDisablingStateForFinalStatusRecovery() throws Exception {
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.DISABLING));
        when(pluginManagerService.uninstall(PLUGIN_ID)).thenReturn(UPDATED_AT);

        AdminPluginResponse response = service.uninstall(PLUGIN_ID);

        assertEquals(PluginStatus.UNINSTALLED, response.status());
        assertEquals(UPDATED_AT, response.updatedAt());
        verify(pluginManagerService).uninstall(PLUGIN_ID);
        JsonNode detail = capturedAudit("PLUGIN_UNINSTALLED", "SUCCESS");
        assertEquals("DISABLING", detail.get("beforeStatus").asText());
        assertEquals("UNINSTALLED", detail.get("afterStatus").asText());
    }

    @Test
    void missingPluginShouldReturn404AndWriteBlockedAudit() {
        when(pluginManagerService.getPlugin(PLUGIN_ID))
            .thenThrow(new NoSuchElementException("internal path details"));

        BizException thrown = assertThrows(BizException.class, () -> service.enable(PLUGIN_ID));

        assertEquals(HttpStatus.NOT_FOUND, thrown.getHttpStatus());
        assertEquals("插件不存在", thrown.getMessage());
        verify(auditLogWriter).write(
            eq("ADMIN"), eq(9001L), eq("PLUGIN_ENABLED"), eq("PLUGIN"), eq(null),
            eq("BLOCKED"), anyString()
        );
    }

    @Test
    void dependencyConflictShouldReturnSafe409AndAuditBlocked() {
        PluginRow active = plugin(PluginStatus.ACTIVE);
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(active);
        doThrow(new PluginManagerService.PluginDependencyConflictException(
            "internal dependent details"
        ))
            .when(pluginManagerService).disable(PLUGIN_ID);

        BizException thrown = assertThrows(BizException.class, () -> service.disable(PLUGIN_ID));

        assertEquals(HttpStatus.CONFLICT, thrown.getHttpStatus());
        assertEquals("插件仍被其他插件依赖，不能停用", thrown.getMessage());
        verify(auditLogWriter).write(
            eq("ADMIN"), eq(9001L), eq("PLUGIN_DISABLED"), eq("PLUGIN"), eq(null),
            eq("BLOCKED"), anyString()
        );
    }

    @Test
    void uninstallDependencyConflictShouldReturnSafe409AndAuditBlocked() {
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.STOPPED));
        doThrow(new PluginManagerService.PluginDependencyConflictException(
            "internal dependent details"
        )).when(pluginManagerService).uninstall(PLUGIN_ID);

        BizException thrown = assertThrows(BizException.class, () -> service.uninstall(PLUGIN_ID));

        assertEquals(HttpStatus.CONFLICT, thrown.getHttpStatus());
        assertEquals("插件仍被其他插件依赖，不能卸载", thrown.getMessage());
        verify(auditLogWriter).write(
            eq("ADMIN"), eq(9001L), eq("PLUGIN_UNINSTALLED"), eq("PLUGIN"), eq(null),
            eq("BLOCKED"), anyString()
        );
    }

    @Test
    void unexpectedLifecycleFailureShouldRemain500AndAuditFail() {
        IllegalStateException failure = new IllegalStateException("internal database details");
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.ACTIVE));
        doThrow(failure).when(pluginManagerService).disable(PLUGIN_ID);

        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> service.disable(PLUGIN_ID)
        );

        assertEquals(failure, thrown);
        verify(auditLogWriter).write(
            eq("ADMIN"), eq(9001L), eq("PLUGIN_DISABLED"), eq("PLUGIN"), eq(null),
            eq("FAIL"), anyString()
        );
    }

    @Test
    void successfulLifecycleShouldNotDependOnRefreshQuery() {
        when(pluginManagerService.getPlugin(PLUGIN_ID))
            .thenReturn(plugin(PluginStatus.INSTALLED))
            .thenThrow(new IllegalStateException("refresh unavailable"));
        when(pluginManagerService.enable(PLUGIN_ID)).thenReturn(UPDATED_AT);

        AdminPluginResponse response = service.enable(PLUGIN_ID);

        assertEquals(PluginStatus.ACTIVE, response.status());
        verify(pluginManagerService, times(1)).getPlugin(PLUGIN_ID);
    }

    @Test
    void auditFailureShouldNotChangeSuccessfulLifecycleResponse() {
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.INSTALLED));
        when(pluginManagerService.enable(PLUGIN_ID)).thenReturn(UPDATED_AT);
        doThrow(new IllegalStateException("audit unavailable"))
            .when(auditLogWriter).write(
                anyString(), anyLong(), anyString(), anyString(), any(), anyString(), anyString()
            );

        AdminPluginResponse response = service.enable(PLUGIN_ID);

        assertEquals(PluginStatus.ACTIVE, response.status());
        verify(pluginManagerService).enable(PLUGIN_ID);
    }

    @Test
    void concurrentDisableAndUninstallShouldPreserveUninstalledTerminalState() throws Exception {
        AtomicReference<PluginStatus> status = new AtomicReference<>(PluginStatus.ACTIVE);
        CountDownLatch disableEntered = new CountDownLatch(1);
        CountDownLatch allowDisable = new CountDownLatch(1);
        CountDownLatch uninstallTaskStarted = new CountDownLatch(1);
        AtomicReference<Thread> uninstallThread = new AtomicReference<>();
        when(pluginManagerService.getPlugin(PLUGIN_ID))
            .thenAnswer(invocation -> plugin(status.get()));
        org.mockito.Mockito.doAnswer(invocation -> {
            disableEntered.countDown();
            assertTrue(allowDisable.await(5, TimeUnit.SECONDS));
            status.set(status.get() == PluginStatus.UNINSTALLED
                ? PluginStatus.ERROR
                : PluginStatus.STOPPED);
            return UPDATED_AT;
        }).when(pluginManagerService).disable(PLUGIN_ID);
        org.mockito.Mockito.doAnswer(invocation -> {
            status.set(PluginStatus.UNINSTALLED);
            return UPDATED_AT;
        }).when(pluginManagerService).uninstall(PLUGIN_ID);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AdminPluginResponse> disable = executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(authentication);
                try {
                    return service.disable(PLUGIN_ID);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            assertTrue(disableEntered.await(5, TimeUnit.SECONDS));
            Future<AdminPluginResponse> uninstall = executor.submit(() -> {
                uninstallThread.set(Thread.currentThread());
                uninstallTaskStarted.countDown();
                SecurityContextHolder.getContext().setAuthentication(authentication);
                try {
                    return service.uninstall(PLUGIN_ID);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });

            assertTrue(uninstallTaskStarted.await(5, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (uninstallThread.get().getState() != Thread.State.BLOCKED
                    && !uninstall.isDone()
                    && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(5);
            }
            assertEquals(Thread.State.BLOCKED, uninstallThread.get().getState());
            allowDisable.countDown();

            assertEquals(PluginStatus.STOPPED, disable.get(5, TimeUnit.SECONDS).status());
            assertEquals(PluginStatus.UNINSTALLED, uninstall.get(5, TimeUnit.SECONDS).status());
            assertEquals(PluginStatus.UNINSTALLED, status.get());
        } finally {
            allowDisable.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void invalidPluginIdShouldReturn400BeforeLookup() {
        BizException thrown = assertThrows(BizException.class, () -> service.enable("../plugin"));

        assertEquals(HttpStatus.BAD_REQUEST, thrown.getHttpStatus());
        verify(pluginManagerService, never()).getPlugin(anyString());
    }

    private JsonNode capturedAudit(String action, String result) throws Exception {
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(auditLogWriter).write(
            eq("ADMIN"), eq(9001L), eq(action), eq("PLUGIN"), eq(null),
            eq(result), detail.capture()
        );
        return objectMapper.readTree(detail.getValue());
    }

    private PluginRow plugin(PluginStatus status) {
        PluginRow row = new PluginRow();
        row.setPluginId(PLUGIN_ID);
        row.setName("Hello Plugin");
        row.setDescription("测试插件");
        row.setVersion("1.0.0");
        row.setSdkVersion("0.1.0");
        row.setMinHostVersion("0.1.0");
        row.setMaxHostVersion("0.2.0");
        row.setJavaVersion("17");
        row.setType("FUNCTIONAL");
        row.setStatus(status);
        row.setSignerId("test-signer");
        row.setProvider("Workforce Hub");
        row.setErrorMessage(status == PluginStatus.ERROR ? "启动失败" : null);
        row.setInstalledAt(LocalDateTime.of(2026, 7, 30, 10, 0));
        row.setUpdatedAt(LocalDateTime.of(2026, 7, 30, 10, 0));
        return row;
    }
}
