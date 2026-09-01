package com.workforcehub.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.runtime.PluginLifecycleGuard;
import com.workforcehub.plugin.runtime.PluginManagerService;
import com.workforcehub.plugin.sdk.ActionDescriptor;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.plugin.sdk.ServiceResult;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 插件网关真实数据库权限矩阵集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
@TestPropertySource(properties =
    "workforce.plugin.gateway.csrf.allowed-origins=http://localhost:5174")
class PluginGatewayPermissionMatrixIntegrationTest extends LocalIntegrationTestSupport {

    private static final String PLUGIN_ID = "permission-matrix-plugin";
    private static final String ACTION_CODE = "permission.matrix.execute";
    private static final String DB_CAPABILITY = "PLUGIN_PERMISSION_MATRIX";
    private static final String DESCRIPTOR_ONLY_CAPABILITY = "PLUGIN_DESCRIPTOR_ONLY";
    private static final String TRUSTED_ORIGIN = "http://localhost:5174";
    private static final String VALID_SRI = "sha384-" + "a".repeat(64);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PluginLifecycleGuard lifecycleGuard;

    @MockBean
    private PluginManagerService pluginManagerService;

    private PluginActionHandler handler;

    @BeforeEach
    void setUpPluginFixture() {
        jdbcTemplate.update("DELETE FROM sys_plugin_action_idempotency WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin_action_permission WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin_capability WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin_visibility WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin
                (plugin_id, name, version, sdk_version, java_version, type, status, jar_path, frontend_sha384)
            VALUES (?, ?, '1.0.0', '1.0.0', '17', 'UI', 'ACTIVE', ?, ?)
            """, PLUGIN_ID, PLUGIN_ID, "target/permission-matrix-plugin.jar", VALID_SRI);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_capability (plugin_id, capability_code)
            VALUES (?, ?)
            """, PLUGIN_ID, DB_CAPABILITY);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_action_permission (plugin_id, action_code, capability_code)
            VALUES (?, ?, ?)
            """, PLUGIN_ID, ACTION_CODE, DB_CAPABILITY);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_visibility (plugin_id, scope_type, scope_value)
            VALUES (?, 'USER', '2003')
            """, PLUGIN_ID);

        lifecycleGuard.enable(PLUGIN_ID);
        handler = org.mockito.Mockito.mock(PluginActionHandler.class);
        reset(pluginManagerService);
        PluginRow plugin = new PluginRow();
        plugin.setPluginId(PLUGIN_ID);
        plugin.setStatus(PluginStatus.ACTIVE);
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin);
        when(pluginManagerService.findActionHandler(PLUGIN_ID, ACTION_CODE)).thenReturn(Optional.of(handler));
        when(handler.getDescriptor()).thenReturn(new ActionDescriptor(
            ACTION_CODE,
            "权限矩阵动作",
            "权限矩阵集成测试动作",
            DB_CAPABILITY
        ));
        when(handler.execute(any(InvocationContext.class), anyMap()))
            .thenReturn(ServiceResult.ok(Map.of("executed", true)));
    }

    @AfterEach
    void tearDownPluginFixture() {
        lifecycleGuard.remove(PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin_action_idempotency WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin_visibility WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin_action_permission WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin_capability WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("""
            UPDATE admin_user
            SET status = 'ACTIVE', is_deleted = 0
            WHERE id = 8001
            """);
        jdbcTemplate.update("""
            UPDATE admin_permission
            SET status = 'ACTIVE', is_deleted = 0
            WHERE perm_code = 'ADMIN_PLUGIN_CALL'
            """);
        jdbcTemplate.update("""
            INSERT INTO admin_role_permission (id, role_id, permission_id, created_by)
            SELECT seq.next_id, r.id, p.id, NULL
            FROM admin_role r
            JOIN admin_permission p ON p.perm_code = 'ADMIN_PLUGIN_CALL'
            CROSS JOIN (
                SELECT COALESCE(MAX(id), 6100) + 1 AS next_id
                FROM admin_role_permission
            ) seq
            WHERE r.role_code = 'SUPER_ADMIN'
              AND r.is_deleted = 0
              AND NOT EXISTS (
                  SELECT 1
                  FROM admin_role_permission existing
                  WHERE existing.role_id = r.id
                    AND existing.permission_id = p.id
              )
            """);
    }

    @Test
    void adminDemoWithMappedCapabilityShouldExecuteAction() throws Exception {
        Cookie adminSession = loginAdmin();

        invokeGateway(adminSession)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.executed").value(true));

        verify(handler).execute(any(InvocationContext.class), anyMap());
        assertEquals(1, pluginActionAuditCount());
        Map<String, Object> audit = jdbcTemplate.queryForMap("""
            SELECT operator_type, operator_id, result, target_type, target_id,
                   CAST(detail_json AS CHAR) AS detail_json
            FROM sys_audit_log
            WHERE action_type = 'PLUGIN_ACTION_EXECUTED'
              AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.pluginId')) = ?
            """, PLUGIN_ID);
        assertEquals("ADMIN", audit.get("operator_type"));
        assertEquals(8001L, ((Number) audit.get("operator_id")).longValue());
        assertEquals("SUCCESS", audit.get("result"));
        assertEquals("PLUGIN_ACTION", audit.get("target_type"));
        assertEquals(null, audit.get("target_id"));
        JsonNode detail = objectMapper.readTree((String) audit.get("detail_json"));
        assertEquals(
            Set.of("pluginId", "actionCode", "triggerSource", "serviceCode", "retryable"),
            objectMapper.convertValue(detail, Map.class).keySet()
        );
    }

    @Test
    void visibleEmployeeWithSpoofedIdentityHeadersShouldStillBeDenied() throws Exception {
        Cookie employeeSession = loginEmployee();

        mockMvc.perform(get("/api/employee/plugins/mine").cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].id").value(hasItem(PLUGIN_ID)));

        mockMvc.perform(gatewayRequest(employeeSession)
                .header("X-User-Id", "8001")
                .header("X-User-Type", "ADMIN"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));

        verify(pluginManagerService, never()).getPlugin(PLUGIN_ID);
        verify(handler, never()).execute(any(), anyMap());
        assertEquals(0, pluginActionAuditCount());
    }

    @Test
    void removingCapabilityAfterSuccessfulRequestShouldDenyNextRequest() throws Exception {
        Cookie adminSession = loginAdmin();

        invokeGateway(adminSession)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        jdbcTemplate.update(
            "DELETE FROM sys_plugin_capability WHERE plugin_id = ? AND capability_code = ?",
            PLUGIN_ID,
            DB_CAPABILITY
        );

        invokeGateway(adminSession)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));

        verify(handler, times(1)).execute(any(), anyMap());
        assertEquals(1, pluginActionAuditCount());
    }

    @Test
    void duplicateSameRequestShouldReplayWithoutExecutingAgain() throws Exception {
        Cookie adminSession = loginAdmin();

        invokeGateway(adminSession)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.executed").value(true));
        invokeGateway(adminSession)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.executed").value(true));

        verify(handler, times(1)).execute(any(), anyMap());
        assertEquals(1, pluginActionAuditCount());
    }

    @Test
    void sameKeyWithDifferentParamsShouldConflict() throws Exception {
        when(handler.getDescriptor()).thenReturn(new ActionDescriptor(
            ACTION_CODE,
            "权限矩阵动作",
            "权限矩阵集成测试动作",
            Map.of("value", com.workforcehub.plugin.sdk.ParamSchema.string("值", false)),
            Map.of(),
            DB_CAPABILITY,
            java.time.Duration.ofSeconds(5),
            true
        ));
        Cookie adminSession = loginAdmin();

        mockMvc.perform(gatewayRequest(adminSession, "same-key", "{\"value\":\"a\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(gatewayRequest(adminSession, "same-key", "{\"value\":\"b\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(3001));

        verify(handler, times(1)).execute(any(), anyMap());
        assertEquals(1, pluginActionAuditCount());
    }

    @Test
    void idempotencyKeyShouldBeCaseSensitive() throws Exception {
        Cookie adminSession = loginAdmin();

        mockMvc.perform(gatewayRequest(adminSession, "Case-Key", "{}"))
            .andExpect(status().isOk());
        mockMvc.perform(gatewayRequest(adminSession, "case-key", "{}"))
            .andExpect(status().isOk());

        verify(handler, times(2)).execute(any(), anyMap());
    }

    @Test
    void removingActionMappingShouldDenyBeforeHandlerExecution() throws Exception {
        Cookie adminSession = loginAdmin();
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_action_permission WHERE plugin_id = ? AND action_code = ?",
            PLUGIN_ID,
            ACTION_CODE
        );

        assertGatewayForbidden(adminSession);

        verify(handler, never()).execute(any(), anyMap());
    }

    @Test
    void inactiveAdminPluginCallPermissionShouldDenyExistingSession() throws Exception {
        Cookie adminSession = loginAdmin();
        jdbcTemplate.update(
            "UPDATE admin_permission SET status = 'INACTIVE' WHERE perm_code = 'ADMIN_PLUGIN_CALL'"
        );

        assertGatewayForbidden(adminSession);

        verify(handler, never()).execute(any(), anyMap());
    }

    @Test
    void deletedAdminPluginCallPermissionShouldDenyExistingSession() throws Exception {
        Cookie adminSession = loginAdmin();
        jdbcTemplate.update(
            "UPDATE admin_permission SET is_deleted = 1 WHERE perm_code = 'ADMIN_PLUGIN_CALL'"
        );

        assertGatewayForbidden(adminSession);

        verify(handler, never()).execute(any(), anyMap());
    }

    @Test
    void disabledAdminUserShouldDenyExistingSession() throws Exception {
        Cookie adminSession = loginAdmin();
        jdbcTemplate.update("UPDATE admin_user SET status = 'DISABLED' WHERE id = 8001");

        assertGatewayForbidden(adminSession);

        verify(handler, never()).execute(any(), anyMap());
    }

    @Test
    void removedSuperAdminPluginPermissionRelationShouldDenyExistingSession() throws Exception {
        Cookie adminSession = loginAdmin();
        jdbcTemplate.update("""
            DELETE rp
            FROM admin_role_permission rp
            JOIN admin_role r ON r.id = rp.role_id
            JOIN admin_permission p ON p.id = rp.permission_id
            WHERE r.role_code = 'SUPER_ADMIN'
              AND p.perm_code = 'ADMIN_PLUGIN_CALL'
            """);

        assertGatewayForbidden(adminSession);

        verify(handler, never()).execute(any(), anyMap());
    }

    @Test
    void hostAuthoritativeDbMappingShouldAllowDescriptorCapabilityMismatch() throws Exception {
        // 网关授权以宿主 action_permission 映射为权威，不要求插件 descriptor 自报能力一致。
        when(handler.getDescriptor()).thenReturn(new ActionDescriptor(
            ACTION_CODE,
            "权限矩阵动作",
            "权限矩阵集成测试动作",
            DESCRIPTOR_ONLY_CAPABILITY
        ));
        Cookie adminSession = loginAdmin();

        invokeGateway(adminSession)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        verify(handler).execute(any(InvocationContext.class), anyMap());
    }

    private ResultActions invokeGateway(Cookie session) throws Exception {
        return mockMvc.perform(gatewayRequest(session));
    }

    private void assertGatewayForbidden(Cookie session) throws Exception {
        invokeGateway(session)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));
    }

    private MockHttpServletRequestBuilder gatewayRequest(Cookie session) {
        return gatewayRequest(session, "permission-matrix-key", "{}");
    }

    private MockHttpServletRequestBuilder gatewayRequest(Cookie session, String idempotencyKey, String content) {
        return post("/api/plugin-gateway/" + PLUGIN_ID + "/actions/" + ACTION_CODE)
            .cookie(session)
            .header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(content);
    }

    private Cookie loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "admin.demo",
                      "password": "Admin@123456"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    private int pluginActionAuditCount() {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_audit_log
            WHERE action_type = 'PLUGIN_ACTION_EXECUTED'
              AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.pluginId')) = ?
            """, Integer.class, PLUGIN_ID);
        return count == null ? 0 : count;
    }

    private Cookie loginEmployee() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "zhangsan.employee",
                      "password": "Employee@123456"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return result.getResponse().getCookie("SESSION");
    }
}
