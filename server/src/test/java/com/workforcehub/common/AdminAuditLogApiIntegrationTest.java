package com.workforcehub.common;

import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class AdminAuditLogApiIntegrationTest extends LocalIntegrationTestSupport {

    private static final String ADMIN_LOGIN_NAME = "admin.demo";
    private static final String ADMIN_PASSWORD = "Admin@123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("管理员应能分页查询系统审计日志")
    void adminShouldListAuditLogs() throws Exception {
        Cookie session = login();
        jdbcTemplate.update("""
            INSERT INTO sys_audit_log
                (id, operator_type, operator_id, action_type, target_type, target_id, result, detail_json, occurred_at)
            VALUES
                (99001, 'ADMIN', 8001, 'TEST_AUDIT_QUERY', 'TEST_TARGET', 1001, 'SUCCESS', JSON_OBJECT('source', 'test'), '2026-06-29 10:00:00')
            """);

        mockMvc.perform(get("/api/admin/audit-logs")
                .cookie(session)
                .param("actionType", "TEST_AUDIT_QUERY")
                .param("pageNo", "1")
                .param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.list[0].actionType").value("TEST_AUDIT_QUERY"))
            .andExpect(jsonPath("$.data.list[0].targetType").value("TEST_TARGET"));
    }

    @Test
    @DisplayName("管理员审计列表应展示插件动作语义")
    void adminShouldSeePluginActionAuditNames() throws Exception {
        Cookie session = login();
        jdbcTemplate.update("""
            INSERT INTO sys_audit_log
                (id, operator_type, operator_id, action_type, target_type, result, detail_json, occurred_at)
            VALUES
                (99002, 'SYSTEM', NULL, 'PLUGIN_ACTION_EXECUTED', 'PLUGIN_ACTION', 'FAIL',
                 JSON_OBJECT('pluginId', 'audit-plugin', 'actionCode', 'audit.execute',
                             'triggerSource', 'EVENT_CALLBACK', 'serviceCode', 4000,
                             'retryable', false),
                 '2026-06-29 10:00:01'),
                (99003, 'SYSTEM', NULL, 'PLUGIN_EVENT_SUBSCRIBED', 'PLUGIN_EVENT_SUBSCRIPTION', 'SUCCESS',
                 JSON_OBJECT('pluginId', 'audit-plugin', 'eventType', 'WORKFLOW_APPROVED',
                             'actionCode', 'audit.execute', 'triggerSource', 'EVENT_CALLBACK'),
                 '2026-06-29 10:00:02')
            """);

        mockMvc.perform(get("/api/admin/audit-logs")
                .cookie(session)
                .param("actionType", "PLUGIN_ACTION_EXECUTED")
                .param("pageNo", "1")
                .param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.list[0].operatorTypeName").value("系统"))
            .andExpect(jsonPath("$.data.list[0].actionTypeName").value("执行插件动作"))
            .andExpect(jsonPath("$.data.list[0].targetTypeName").value("插件动作"))
            .andExpect(jsonPath("$.data.list[0].resultName").value("失败"));

        mockMvc.perform(get("/api/admin/audit-logs")
                .cookie(session)
                .param("actionType", "PLUGIN_EVENT_SUBSCRIBED")
                .param("pageNo", "1")
                .param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.list[0].actionTypeName").value("订阅插件事件"))
            .andExpect(jsonPath("$.data.list[0].targetTypeName").value("插件事件订阅"));
    }

    private Cookie login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "%s"
                    }
                    """.formatted(ADMIN_LOGIN_NAME, ADMIN_PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();

        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "登录成功后应返回 SESSION Cookie");
        return session;
    }
}
