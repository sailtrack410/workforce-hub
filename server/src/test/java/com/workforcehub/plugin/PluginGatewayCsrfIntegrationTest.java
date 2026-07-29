package com.workforcehub.plugin;

import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
@TestPropertySource(properties =
    "workforce.plugin.gateway.csrf.allowed-origins=http://localhost:5174")
class PluginGatewayCsrfIntegrationTest extends LocalIntegrationTestSupport {

    private static final String MISSING_PLUGIN_ID = "csrf-missing-plugin-20260728";
    private static final String ACTION_CODE = "test.action";
    private static final String TRUSTED_ORIGIN = "http://localhost:5174";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void ensureMissingPlugin() {
        jdbcTemplate.update("DELETE FROM sys_plugin WHERE plugin_id = ?", MISSING_PLUGIN_ID);
    }

    @Test
    void trustedOriginShouldReachPermissionCheckWithRealSession() throws Exception {
        Cookie session = login();

        mockMvc.perform(gatewayRequest()
                .cookie(session)
                .header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(4003))
            .andExpect(jsonPath("$.message").value("权限不足"));
    }

    @Test
    void maliciousAndMissingSourcesShouldBeRejectedWithSameSession() throws Exception {
        Cookie session = login();

        mockMvc.perform(gatewayRequest()
                .cookie(session)
                .header(HttpHeaders.ORIGIN, "https://attacker.example"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(4003))
            .andExpect(jsonPath("$.message").value("请求来源不受信任"));

        mockMvc.perform(gatewayRequest().cookie(session))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(4003))
            .andExpect(jsonPath("$.message").value("请求来源不受信任"));
    }

    @Test
    void anonymousRequestShouldRemainUnauthorized() throws Exception {
        mockMvc.perform(gatewayRequest()
                .header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN))
            .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder gatewayRequest() {
        return post("/api/plugin-gateway/" + MISSING_PLUGIN_ID + "/actions/" + ACTION_CODE)
            .header("Idempotency-Key", "csrf-test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}");
    }

    private Cookie login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginName\":\"zhangsan.employee\",\"password\":\"Employee@123456\"}"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();

        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "登录成功后应返回 SESSION Cookie");
        return session;
    }
}
