package com.workforcehub.auth;

import com.workforcehub.auth.domain.AuthUserAccount;
import com.workforcehub.auth.domain.OrgUser;
import com.workforcehub.auth.mapper.AuthUserAccountMapper;
import com.workforcehub.auth.mapper.OrgUserMapper;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-auth 认证接口自动化冒烟
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleAuthApiIntegrationTest extends LocalIntegrationTestSupport {

    private static final String ADMIN_LOGIN_NAME = "admin.demo";
    private static final String ADMIN_PASSWORD = "Admin@123456";
    private static final String EMPLOYEE_LOGIN_NAME = "zhangsan.employee";
    private static final String EMPLOYEE_PASSWORD = "Employee@123456";
    private static final String MANAGER_LOGIN_NAME = "lisi.manager";
    private static final String PROBATION_LOGIN_NAME = "wangwu.employee";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private AuthUserAccountMapper accountMapper;

    @Autowired
    private OrgUserMapper orgUserMapper;

    @MockBean
    private JavaMailSender javaMailSender;

    @Test
    @DisplayName("后台管理员应能登录并查询当前登录信息")
    void adminShouldLoginAndQueryCurrentUser() throws Exception {
        Cookie session = login("/api/admin/auth/login", ADMIN_LOGIN_NAME, ADMIN_PASSWORD, "auth-admin-login-001");

        mockMvc.perform(get("/api/admin/auth/me")
                .cookie(session)
                .header("X-Request-Id", "auth-admin-me-001"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", "auth-admin-me-001"))
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.requestId").value("auth-admin-me-001"))
            .andExpect(jsonPath("$.data.userId").value(8001))
            .andExpect(jsonPath("$.data.userType").value("ADMIN"))
            .andExpect(jsonPath("$.data.displayName").value("演示管理员"))
            .andExpect(jsonPath("$.data.loginName").value(ADMIN_LOGIN_NAME));
    }

    @Test
    @DisplayName("员工应能登录查询 me 并在退出后失效")
    void employeeShouldLoginQueryMeAndLogout() throws Exception {
        Cookie session = login("/api/employee/auth/login", EMPLOYEE_LOGIN_NAME, EMPLOYEE_PASSWORD, "auth-emp-login-001");

        mockMvc.perform(get("/api/employee/auth/me")
                .cookie(session)
                .header("X-Request-Id", "auth-emp-me-001"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", "auth-emp-me-001"))
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value(2003))
            .andExpect(jsonPath("$.data.userType").value("USER"))
            .andExpect(jsonPath("$.data.displayName").value("张三"))
            .andExpect(jsonPath("$.data.loginName").value(EMPLOYEE_LOGIN_NAME))
            .andExpect(jsonPath("$.data.primaryDeptId").value(111))
            .andExpect(jsonPath("$.data.positionId").value(3001))
            .andExpect(jsonPath("$.data.managerUserId").value(2002))
            .andExpect(jsonPath("$.data.employmentStatus").value("ACTIVE"));

        mockMvc.perform(post("/api/employee/auth/logout")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/employee/auth/me")
                .cookie(session))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401))
            .andExpect(jsonPath("$.message").value("未登录或会话已过期"));
    }

    @Test
    @DisplayName("未登录访问 me 与 bind 接口应被拒绝")
    void unauthenticatedRequestsShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/admin/auth/me")
                .header("X-Request-Id", "auth-unauth-admin-001"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("X-Request-Id", "auth-unauth-admin-001"))
            .andExpect(jsonPath("$.code").value(401))
            .andExpect(jsonPath("$.requestId").value("auth-unauth-admin-001"));

        mockMvc.perform(post("/api/employee/auth/email/bind")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-Id", "auth-unauth-bind-001")
                .content("""
                    {
                      "email": "bind.unauth@example.com",
                      "code": "123456"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("X-Request-Id", "auth-unauth-bind-001"))
            .andExpect(jsonPath("$.code").value(401))
            .andExpect(jsonPath("$.requestId").value("auth-unauth-bind-001"));
    }

    @Test
    @DisplayName("发送验证码应成功并受重发频控保护")
    void sendCodeShouldWorkAndBeThrottled() throws Exception {
        doNothing().when(javaMailSender).send(any(SimpleMailMessage.class));
        String email = "bind." + uniqueSuffix() + "@example.com";

        mockMvc.perform(post("/api/employee/auth/email/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-Id", "auth-send-code-001")
                .content("""
                    {
                      "email": "%s",
                      "purpose": "BIND"
                    }
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", "auth-send-code-001"))
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.requestId").value("auth-send-code-001"));

        Object code = redisTemplate.opsForValue().get("auth:email:code:BIND:" + email);
        assertNotNull(code, "验证码发送成功后应写入 Redis");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertNotNull(message, "应向邮件发送器提交消息");
        assertEquals(email, message.getTo()[0]);
        assertTrue(message.getText().contains(code.toString()), "邮件正文应包含验证码");

        mockMvc.perform(post("/api/employee/auth/email/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "purpose": "BIND"
                    }
                    """.formatted(email)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(429))
            .andExpect(jsonPath("$.message").value("验证码发送过于频繁，请稍后再试"));
    }

    @Test
    @DisplayName("员工绑定邮箱后应写回账号邮箱与验证时间")
    void employeeShouldBindEmailSuccessfully() throws Exception {
        doNothing().when(javaMailSender).send(any(SimpleMailMessage.class));
        Cookie session = login("/api/employee/auth/login", EMPLOYEE_LOGIN_NAME, EMPLOYEE_PASSWORD, "auth-bind-login-001");
        String email = "bind." + uniqueSuffix() + "@example.com";

        sendCode(email, "BIND");
        String code = readCode(email, "BIND");

        mockMvc.perform(post("/api/employee/auth/email/bind")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "code": "%s"
                    }
                    """.formatted(email, code)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        AuthUserAccount account = accountMapper.selectByLoginName(EMPLOYEE_LOGIN_NAME);
        assertNotNull(account, "员工账号应存在");
        assertEquals(email, account.getEmail());
        assertNotNull(account.getEmailVerifiedAt(), "邮箱绑定成功后应记录验证时间");
    }

    @Test
    @DisplayName("密码重置后新密码应能登录，旧密码应失效")
    void resetPasswordShouldTakeEffectImmediately() throws Exception {
        doNothing().when(javaMailSender).send(any(SimpleMailMessage.class));
        String newPassword = "Employee@654321";
        String email = "zhangsan@example.com";

        sendCode(email, "RESET_PASSWORD");
        String code = readCode(email, "RESET_PASSWORD");

        mockMvc.perform(post("/api/employee/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-Id", "auth-reset-password-001")
                .content("""
                    {
                      "email": "%s",
                      "code": "%s",
                      "newPassword": "%s"
                    }
                    """.formatted(email, code, newPassword)))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", "auth-reset-password-001"))
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.requestId").value("auth-reset-password-001"));

        mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "%s"
                    }
                    """.formatted(EMPLOYEE_LOGIN_NAME, EMPLOYEE_PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(401))
            .andExpect(jsonPath("$.message").value("用户名或密码错误，剩余尝试次数 4"));

        mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "%s"
                    }
                    """.formatted(EMPLOYEE_LOGIN_NAME, newPassword)))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value(2003));
    }

    @Test
    @DisplayName("连续输错密码后应被锁定")
    void failedLoginShouldBeLockedAfterTooManyAttempts() throws Exception {
        for (int i = 1; i <= 5; i++) {
            int remaining = 5 - i;
            mockMvc.perform(post("/api/employee/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "loginName": "%s",
                          "password": "WrongPassword@123"
                        }
                        """.formatted(MANAGER_LOGIN_NAME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误，剩余尝试次数 " + remaining));
        }

        mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "%s"
                    }
                    """.formatted(MANAGER_LOGIN_NAME, EMPLOYEE_PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(429))
            .andExpect(jsonPath("$.message").value("登录失败次数过多，账号已锁定 30 分钟"));
    }

    @Test
    @DisplayName("不允许登录的员工生命周期状态应返回明确提示")
    void employmentStatusShouldBlockLoginWithExplicitMessage() throws Exception {
        OrgUser orgUser = orgUserMapper.selectById(2004L);
        assertNotNull(orgUser, "测试员工档案应存在");
        orgUser.setEmploymentStatus("OFFBOARDED");
        orgUserMapper.updateById(orgUser);

        mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "%s"
                    }
                    """.formatted(PROBATION_LOGIN_NAME, EMPLOYEE_PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("员工已离职，无法登录"));
    }

    private Cookie login(String path, String loginName, String password, String requestId) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-Id", requestId)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "%s"
                    }
                    """.formatted(loginName, password)))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", requestId))
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();

        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "登录成功后应返回 SESSION Cookie");
        return session;
    }

    private void sendCode(String email, String purpose) throws Exception {
        mockMvc.perform(post("/api/employee/auth/email/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "purpose": "%s"
                    }
                    """.formatted(email, purpose)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    private String readCode(String email, String purpose) {
        Object code = redisTemplate.opsForValue().get("auth:email:code:" + purpose + ":" + email);
        assertNotNull(code, "Redis 中应存在验证码");
        return code.toString();
    }

    private String uniqueSuffix() {
        String raw = String.valueOf(System.nanoTime());
        return raw.substring(raw.length() - 6);
    }
}
