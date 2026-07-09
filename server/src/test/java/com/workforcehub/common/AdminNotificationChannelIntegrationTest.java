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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后台通知渠道配置接口集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class AdminNotificationChannelIntegrationTest extends LocalIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("管理员应能维护邮件通知渠道且不返回密钥")
    void adminShouldManageEmailChannelWithoutLeakingSecret() throws Exception {
        Cookie adminSession = login("admin.demo", "Admin@123456");

        mockMvc.perform(get("/api/admin/notification-channels/EMAIL_DEFAULT")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.channelCode").value("EMAIL_DEFAULT"))
            .andExpect(jsonPath("$.data.channelType").value("EMAIL"))
            .andExpect(jsonPath("$.data.enabled").value(false))
            .andExpect(jsonPath("$.data.hasSecret").value(false))
            .andExpect(jsonPath("$.data.secretValue").doesNotExist());

        mockMvc.perform(put("/api/admin/notification-channels/EMAIL_DEFAULT")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "enabled": true,
                      "host": "smtp.example.com",
                      "port": 465,
                      "username": "notice@example.com",
                      "secretValue": "Mail@123456",
                      "fromAddress": "notice@example.com",
                      "fromName": "Workforce Hub",
                      "sslEnabled": true,
                      "starttlsEnabled": false,
                      "timeoutMillis": 5000
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.enabled").value(true))
            .andExpect(jsonPath("$.data.host").value("smtp.example.com"))
            .andExpect(jsonPath("$.data.port").value(465))
            .andExpect(jsonPath("$.data.hasSecret").value(true))
            .andExpect(jsonPath("$.data.secretValue").doesNotExist());

        mockMvc.perform(put("/api/admin/notification-channels/EMAIL_DEFAULT")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "enabled": false,
                      "host": "smtp2.example.com",
                      "port": 587,
                      "username": "notice2@example.com",
                      "secretValue": "",
                      "fromAddress": "notice2@example.com",
                      "fromName": "Workforce Hub",
                      "sslEnabled": false,
                      "starttlsEnabled": true,
                      "timeoutMillis": 6000
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enabled").value(false))
            .andExpect(jsonPath("$.data.host").value("smtp2.example.com"))
            .andExpect(jsonPath("$.data.hasSecret").value(true))
            .andExpect(jsonPath("$.data.secretValue").doesNotExist());

        String secretValue = jdbcTemplate.queryForObject("""
            SELECT secret_value
            FROM notify_channel_config
            WHERE channel_code = 'EMAIL_DEFAULT'
            """, String.class);
        assertEquals("Mail@123456", secretValue);

        Long auditCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_audit_log
            WHERE action_type IN ('NOTIFY_CHANNEL_ENABLED', 'NOTIFY_CHANNEL_DISABLED')
              AND target_type = 'NOTIFY_CHANNEL'
            """, Long.class);
        assertEquals(2L, auditCount);
    }

    @Test
    @DisplayName("缺少通知配置权限的管理员不允许维护邮件通知渠道")
    void adminWithoutPermissionShouldBeRejected() throws Exception {
        jdbcTemplate.update("""
            INSERT INTO admin_user (id, username, password_hash, display_name, email, status, is_deleted)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0)
            """, 8101L, "limited.admin", passwordEncoder.encode("Limited@123456"), "受限管理员", "limited.admin@example.com");
        Cookie session = login("limited.admin", "Limited@123456");

        mockMvc.perform(get("/api/admin/notification-channels/EMAIL_DEFAULT")
                .cookie(session))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));
    }

    @Test
    @DisplayName("测试邮件使用当前表单配置并记录脱敏审计")
    void adminShouldTestEmailChannelWithCurrentFormConfig() throws Exception {
        Cookie adminSession = login("admin.demo", "Admin@123456");

        mockMvc.perform(post("/api/admin/notification-channels/EMAIL_DEFAULT/test")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "recipient": "admin.demo@example.com",
                      "enabled": true,
                      "host": "127.0.0.1",
                      "port": 1,
                      "username": "notice@example.com",
                      "secretValue": "Mail@123456",
                      "fromAddress": "notice@example.com",
                      "fromName": "Workforce Hub",
                      "sslEnabled": false,
                      "starttlsEnabled": false,
                      "timeoutMillis": 100
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.success").value(false))
            .andExpect(jsonPath("$.data.message").value(not("Mail@123456")));

        Long auditCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_audit_log
            WHERE action_type = 'NOTIFY_CHANNEL_TEST'
              AND target_type = 'NOTIFY_CHANNEL'
              AND detail_json NOT LIKE '%Mail@123456%'
            """, Long.class);
        assertEquals(1L, auditCount);
    }

    @Test
    @DisplayName("测试邮件收件人必须是管理员本人邮箱")
    void testRecipientShouldBeCurrentAdminEmail() throws Exception {
        Cookie adminSession = login("admin.demo", "Admin@123456");

        mockMvc.perform(post("/api/admin/notification-channels/EMAIL_DEFAULT/test")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "recipient": "other@example.com",
                      "host": "127.0.0.1",
                      "port": 1,
                      "username": "notice@example.com",
                      "secretValue": "Mail@123456",
                      "fromAddress": "notice@example.com",
                      "fromName": "Workforce Hub",
                      "timeoutMillis": 100
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400))
            .andExpect(jsonPath("$.message").value("测试收件人必须是当前管理员邮箱"));
    }

    private Cookie login(String loginName, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "%s"
                    }
                    """.formatted(loginName, password)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(cookie().exists("SESSION"))
            .andReturn();

        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "登录成功后应返回 SESSION Cookie");
        return session;
    }
}
