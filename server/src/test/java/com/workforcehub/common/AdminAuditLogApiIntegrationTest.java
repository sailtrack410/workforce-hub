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
