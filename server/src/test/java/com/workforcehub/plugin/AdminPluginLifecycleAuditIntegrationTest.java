package com.workforcehub.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.auth.security.AdminUserDetails;
import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.runtime.PluginManagerService;
import com.workforcehub.support.LocalIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class AdminPluginLifecycleAuditIntegrationTest extends LocalIntegrationTestSupport {

    private static final String PLUGIN_ID = "lifecycle-audit-plugin";
    private static final long ADMIN_ID = 9_001L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PluginManagerService pluginManagerService;

    private final AtomicReference<PluginStatus> pluginStatus = new AtomicReference<>();

    @BeforeEach
    void configureLifecycleManager() {
        reset(pluginManagerService);
        pluginStatus.set(PluginStatus.INSTALLED);
        when(pluginManagerService.getPlugin(PLUGIN_ID))
            .thenAnswer(invocation -> plugin(pluginStatus.get()));
        doAnswer(invocation -> {
            pluginStatus.set(PluginStatus.ACTIVE);
            return null;
        }).when(pluginManagerService).enable(PLUGIN_ID);
        doAnswer(invocation -> {
            pluginStatus.set(PluginStatus.STOPPED);
            return null;
        }).when(pluginManagerService).disable(PLUGIN_ID);
        doAnswer(invocation -> {
            pluginStatus.set(PluginStatus.UNINSTALLED);
            return null;
        }).when(pluginManagerService).uninstall(PLUGIN_ID);
    }

    @Test
    void successfulLifecycleOperationsShouldPersistSanitizedAuditsAndDisplayNames() throws Exception {
        mockMvc.perform(put("/api/admin/plugins/{pluginId}/enable", PLUGIN_ID)
                .with(admin())
                .with(csrf().asHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(put("/api/admin/plugins/{pluginId}/disable", PLUGIN_ID)
                .with(admin())
                .with(csrf().asHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("STOPPED"));
        mockMvc.perform(delete("/api/admin/plugins/{pluginId}", PLUGIN_ID)
                .with(admin())
                .with(csrf().asHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("UNINSTALLED"));

        List<AuditRow> audits = lifecycleAudits();
        assertEquals(3, audits.size());
        assertAudit(audits.get(0), "PLUGIN_ENABLED", "SUCCESS", "INSTALLED", "ACTIVE");
        assertAudit(audits.get(1), "PLUGIN_DISABLED", "SUCCESS", "ACTIVE", "STOPPED");
        assertAudit(audits.get(2), "PLUGIN_UNINSTALLED", "SUCCESS", "STOPPED", "UNINSTALLED");

        assertAuditDisplay("PLUGIN_ENABLED", "启用插件", "成功");
        assertAuditDisplay("PLUGIN_DISABLED", "停用插件", "成功");
        assertAuditDisplay("PLUGIN_UNINSTALLED", "卸载插件", "成功");
    }

    @Test
    void blockedLifecycleOperationShouldPersistBlockedAuditWithoutInternalFields() throws Exception {
        pluginStatus.set(PluginStatus.ERROR);

        mockMvc.perform(put("/api/admin/plugins/{pluginId}/enable", PLUGIN_ID)
                .with(admin())
                .with(csrf().asHeader()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("当前插件状态不允许启用"));

        List<AuditRow> audits = lifecycleAudits();
        assertEquals(1, audits.size());
        assertAudit(audits.get(0), "PLUGIN_ENABLED", "BLOCKED", "ERROR", "ERROR");
        assertAuditDisplay("PLUGIN_ENABLED", "启用插件", "已阻止");
    }

    @Test
    void unexpectedLifecycleFailureShouldReturn500AndPersistFailAuditWithoutExceptionText() throws Exception {
        pluginStatus.set(PluginStatus.ACTIVE);
        doThrow(new IllegalStateException("private lifecycle exception text"))
            .when(pluginManagerService).disable(PLUGIN_ID);

        mockMvc.perform(put("/api/admin/plugins/{pluginId}/disable", PLUGIN_ID)
                .with(admin())
                .with(csrf().asHeader()))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("系统内部错误"));

        List<AuditRow> audits = lifecycleAudits();
        assertEquals(1, audits.size());
        assertAudit(audits.get(0), "PLUGIN_DISABLED", "FAIL", "ACTIVE", "ACTIVE");
        assertFalse(audits.get(0).detailJson().contains("private lifecycle exception text"));
        assertAuditDisplay("PLUGIN_DISABLED", "停用插件", "失败");
    }

    @Test
    void legacyFailedResultShouldRemainDisplayedAsFailure() throws Exception {
        jdbcTemplate.update("""
            INSERT INTO sys_audit_log
                (id, operator_type, operator_id, action_type, target_type, result, detail_json)
            VALUES
                (9990001, 'ADMIN', ?, 'PLUGIN_DISABLED', 'PLUGIN', 'FAILED',
                 JSON_OBJECT('pluginId', ?, 'beforeStatus', 'ACTIVE', 'afterStatus', 'ACTIVE'))
            """, ADMIN_ID, PLUGIN_ID);

        assertAuditDisplay("PLUGIN_DISABLED", "停用插件", "失败");
    }

    private void assertAudit(
            AuditRow audit,
            String actionType,
            String result,
            String beforeStatus,
            String afterStatus) throws Exception {
        assertEquals("ADMIN", audit.operatorType());
        assertEquals(ADMIN_ID, audit.operatorId());
        assertEquals(actionType, audit.actionType());
        assertEquals("PLUGIN", audit.targetType());
        assertEquals(result, audit.result());

        JsonNode detail = objectMapper.readTree(audit.detailJson());
        Set<String> fields = new HashSet<>();
        detail.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("pluginId", "beforeStatus", "afterStatus"), fields);
        assertEquals(PLUGIN_ID, detail.path("pluginId").asText());
        assertEquals(beforeStatus, detail.path("beforeStatus").asText());
        assertEquals(afterStatus, detail.path("afterStatus").asText());
        assertFalse(audit.detailJson().contains("jarPath"));
        assertFalse(audit.detailJson().contains("jarSha256"));
        assertFalse(audit.detailJson().contains("signature"));
        assertFalse(audit.detailJson().contains("private/plugin.jar"));
        assertFalse(audit.detailJson().contains("private-digest"));
        assertFalse(audit.detailJson().contains("private plugin error"));
    }

    private void assertAuditDisplay(String actionType, String actionName, String resultName) throws Exception {
        mockMvc.perform(get("/api/admin/system/audit-logs")
                .with(admin())
                .param("actionType", actionType)
                .param("targetType", "PLUGIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.list[0].actionTypeName").value(actionName))
            .andExpect(jsonPath("$.data.list[0].targetTypeName").value("插件"))
            .andExpect(jsonPath("$.data.list[0].resultName").value(resultName));
    }

    private List<AuditRow> lifecycleAudits() {
        return jdbcTemplate.query("""
            SELECT operator_type, operator_id, action_type, target_type, result, detail_json
            FROM sys_audit_log
            WHERE target_type = 'PLUGIN'
              AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.pluginId')) = ?
            ORDER BY occurred_at, id
            """, (rs, rowNum) -> new AuditRow(
                rs.getString("operator_type"),
                rs.getLong("operator_id"),
                rs.getString("action_type"),
                rs.getString("target_type"),
                rs.getString("result"),
                rs.getString("detail_json")
            ), PLUGIN_ID);
    }

    private RequestPostProcessor admin() {
        AdminUserDetails principal = new AdminUserDetails(
            ADMIN_ID,
            "plugin.audit.admin",
            "unused",
            "插件审计管理员",
            "ACTIVE",
            List.of(),
            List.of("ADMIN_PLUGIN_MANAGE")
        );
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            principal.getAuthorities()
        ));
    }

    private PluginRow plugin(PluginStatus status) {
        PluginRow row = new PluginRow();
        row.setPluginId(PLUGIN_ID);
        row.setName("Lifecycle Audit Plugin");
        row.setVersion("1.0.0");
        row.setSdkVersion("0.1.0");
        row.setJavaVersion("17");
        row.setType("FUNCTIONAL");
        row.setStatus(status);
        row.setJarPath("/private/plugin.jar");
        row.setJarSha256("private-digest");
        row.setSignature("private-signature".getBytes(StandardCharsets.UTF_8));
        row.setErrorMessage("private plugin error");
        row.setInstalledAt(LocalDateTime.of(2026, 7, 30, 10, 0));
        row.setUpdatedAt(LocalDateTime.of(2026, 7, 30, 10, 0));
        return row;
    }

    private record AuditRow(
        String operatorType,
        long operatorId,
        String actionType,
        String targetType,
        String result,
        String detailJson
    ) {}
}
