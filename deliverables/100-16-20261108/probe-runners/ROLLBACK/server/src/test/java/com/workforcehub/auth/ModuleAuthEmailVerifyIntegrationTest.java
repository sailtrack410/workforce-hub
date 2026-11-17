package com.workforcehub.auth;

import com.workforcehub.auth.service.EmailCodeService;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 员工端登录邮箱二验流程冒烟
 * - 开启 workforce.auth.email-verify.enabled
 * - 覆盖：凭证 OK 后返回 requiresEmailCode；发送验证码；带验证码完成登录；账号无邮箱直接放行；登录验证码 60s 节流
 */
@SpringBootTest(properties = "test.context.email-verify=true")
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleAuthEmailVerifyIntegrationTest extends LocalIntegrationTestSupport {

    private static final String EMPLOYEE_LOGIN_NAME = "zhangsan.employee";
    private static final String EMPLOYEE_EMAIL = "zhangsan@example.com";
    private static final String EMPLOYEE_PASSWORD = "Employee@123456";

    static {
        System.setProperty("test.workforce.auth.email-verify.enabled", "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private EmailCodeService emailCodeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private JavaMailSender javaMailSender;

    @AfterAll
    static void clearTestEmailVerifyProperty() {
        System.clearProperty("test.workforce.auth.email-verify.enabled");
    }

    @Test
    @DisplayName("已绑定邮箱的员工：凭证通过后应返回 requiresEmailCode 且未签发 SESSION")
    void loginRequiresEmailCodeWhenAccountHasEmail() throws Exception {
        mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "%s"
                    }
                    """.formatted(EMPLOYEE_LOGIN_NAME, EMPLOYEE_PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.requiresEmailCode").value(true))
            .andExpect(jsonPath("$.data.maskedEmail").exists())
            .andExpect(cookie().doesNotExist("SESSION"));
    }

    @Test
    @DisplayName("发送登录验证码：identifier 可以是登录名或邮箱，返回 maskedEmail")
    void sendLoginCodeShouldAcceptLoginNameAndEmail() throws Exception {
        doNothing().when(javaMailSender).send(any(SimpleMailMessage.class));

        mockMvc.perform(post("/api/employee/auth/login/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "identifier": "%s"
                    }
                    """.formatted(EMPLOYEE_LOGIN_NAME)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.maskedEmail").exists());

        Object code = redisTemplate.opsForValue().get(emailCodeService.buildCodeKey(EMPLOYEE_EMAIL, "LOGIN"));
        assertNotNull(code, "登录验证码应写入 Redis");

        // 邮箱也能触发；但 60s 内重发会被节流
        mockMvc.perform(post("/api/employee/auth/login/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "identifier": "%s"
                    }
                    """.formatted(EMPLOYEE_EMAIL)))
            .andExpect(status().is(429))
            .andExpect(jsonPath("$.code").value(100429));
    }

    @Test
    @DisplayName("已绑定邮箱的员工：带验证码登录应签发 SESSION")
    void loginWithEmailCodeShouldIssueSession() throws Exception {
        doNothing().when(javaMailSender).send(any(SimpleMailMessage.class));

        // 触发验证码
        mockMvc.perform(post("/api/employee/auth/login/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "identifier": "%s"
                    }
                    """.formatted(EMPLOYEE_LOGIN_NAME)))
            .andExpect(status().isOk());

        Object stored = redisTemplate.opsForValue().get(emailCodeService.buildCodeKey(EMPLOYEE_EMAIL, "LOGIN"));
        assertNotNull(stored, "登录验证码应已生成");
        String code = stored.toString();

        // 用邮箱 + 密码 + 验证码登录（验证 identifier 接受邮箱）
        mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "%s",
                      "emailCode": "%s"
                    }
                    """.formatted(EMPLOYEE_EMAIL, EMPLOYEE_PASSWORD, code)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value(2003))
            .andExpect(jsonPath("$.data.loginName").value(EMPLOYEE_LOGIN_NAME))
            .andExpect(jsonPath("$.data.requiresEmailCode").value(false))
            .andExpect(cookie().exists("SESSION"));
    }

    @Test
    @DisplayName("已绑定邮箱的员工：错误验证码应返回 401")
    void loginWithInvalidEmailCodeShouldFail() throws Exception {
        mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "%s",
                      "emailCode": "000000"
                    }
                    """.formatted(EMPLOYEE_LOGIN_NAME, EMPLOYEE_PASSWORD)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(100401))
            .andExpect(jsonPath("$.message").value("邮箱验证码错误或已过期"));
    }

    @Test
    @DisplayName("未绑定邮箱的员工：凭证通过后应先绑定邮箱，再用绑定验证码完成登录")
    void loginWithoutBoundEmailShouldRequireEmailBinding() throws Exception {
        doNothing().when(javaMailSender).send(any(SimpleMailMessage.class));
        jdbcTemplate.update("""
            UPDATE auth_user_account
            SET email = NULL, email_verified_at = NULL
            WHERE login_name = ?
            """, EMPLOYEE_LOGIN_NAME);

        mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "%s"
                    }
                    """.formatted(EMPLOYEE_LOGIN_NAME, EMPLOYEE_PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.requiresEmailCode").value(false))
            .andExpect(jsonPath("$.data.requiresEmailBinding").value(true))
            .andExpect(cookie().doesNotExist("SESSION"));

        String bindEmail = "zhangsan.bind@example.com";
        mockMvc.perform(post("/api/employee/auth/email/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "purpose": "BIND"
                    }
                    """.formatted(bindEmail)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        Object stored = redisTemplate.opsForValue().get(emailCodeService.buildCodeKey(bindEmail, "BIND"));
        assertNotNull(stored, "首次登录绑定邮箱验证码应已生成");

        mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "%s",
                      "bindEmail": "%s",
                      "emailCode": "%s"
                    }
                    """.formatted(EMPLOYEE_LOGIN_NAME, EMPLOYEE_PASSWORD, bindEmail, stored)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value(2003))
            .andExpect(jsonPath("$.data.requiresEmailBinding").value(false))
            .andExpect(cookie().exists("SESSION"));

        String updatedEmail = jdbcTemplate.queryForObject("""
            SELECT email
            FROM auth_user_account
            WHERE login_name = ?
            """, String.class, EMPLOYEE_LOGIN_NAME);
        assertEquals(bindEmail, updatedEmail);
    }
}
