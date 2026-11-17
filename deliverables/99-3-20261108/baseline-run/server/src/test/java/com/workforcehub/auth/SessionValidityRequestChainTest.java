package com.workforcehub.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.auth.controller.AdminAuthController;
import com.workforcehub.auth.controller.EmployeeAuthController;
import com.workforcehub.auth.domain.AdminUser;
import com.workforcehub.auth.domain.AuthUserAccount;
import com.workforcehub.auth.domain.OrgUser;
import com.workforcehub.auth.mapper.AdminUserMapper;
import com.workforcehub.auth.mapper.AuthUserAccountMapper;
import com.workforcehub.auth.mapper.OrgUserMapper;
import com.workforcehub.auth.security.*;
import com.workforcehub.auth.service.*;
import com.workforcehub.common.config.SecurityConfig;
import com.workforcehub.im.repository.ImRepository;
import com.workforcehub.im.websocket.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.socket.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 完整 SecurityFilterChain + 真实登录服务/Provider；仅持久层与外部邮件/频控使用测试替身。 */
@SpringJUnitWebConfig(SessionValidityRequestChainTest.Config.class)
class SessionValidityRequestChainTest {
    @Configuration
    @EnableWebMvc
    @ComponentScan(basePackageClasses = UserTypePathFilter.class)
    @Import({SecurityConfig.class, AdminUserDetailsService.class, EmployeeUserDetailsService.class,
        AdminPermissionService.class, LoginService.class, AdminAuthController.class, EmployeeAuthController.class,
        ImWebSocketHandshakeInterceptor.class, ImWebSocketSessionRegistry.class, ImWebSocketHandler.class,
        ProtectedController.class})
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean AdminUserMapper adminMapper() { return mock(AdminUserMapper.class); }
        @Bean AuthUserAccountMapper accountMapper() { return mock(AuthUserAccountMapper.class); }
        @Bean OrgUserMapper orgMapper() { return mock(OrgUserMapper.class); }
        @Bean AuthLockService lockService() { return mock(AuthLockService.class); }
        @Bean EmailCodeService emailService() { return mock(EmailCodeService.class); }
        @Bean AccountService accountService() { return mock(AccountService.class); }
        @Bean ImRepository imRepository() { return mock(ImRepository.class); }
        @Bean static org.springframework.context.support.PropertySourcesPlaceholderConfigurer properties() {
            var config = new org.springframework.context.support.PropertySourcesPlaceholderConfigurer();
            var props = new Properties();
            props.setProperty("workforce.auth.email-verify.enabled", "false");
            config.setProperties(props);
            return config;
        }
    }

    @RestController
    static class ProtectedController {
        @GetMapping({"/api/admin/attendance/shifts", "/api/employee/session-probe"})
        String probe() { return "protected"; }
    }

    @Autowired WebApplicationContext context;
    @Autowired AdminUserMapper admins;
    @Autowired AuthUserAccountMapper accounts;
    @Autowired OrgUserMapper orgs;
    @Autowired ImRepository imRepository;
    @Autowired ImWebSocketHandshakeInterceptor handshake;
    @Autowired ImWebSocketHandler handler;
    @Autowired ImWebSocketSessionRegistry registry;
    MockMvc mvc;
    AdminUser admin;
    AuthUserAccount employee;
    OrgUser org;

    @BeforeEach
    void setup() {
        reset(admins, accounts, orgs, imRepository);
        registry.clear();
        SecurityContextHolder.clearContext();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String hash = context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class).encode("Fixture-pass-99");
        admin = new AdminUser();
        admin.setId(101L); admin.setUsername("fixture.admin"); admin.setPasswordHash(hash);
        admin.setDisplayName("Fixture"); admin.setStatus("ACTIVE");
        when(admins.selectByUsername("fixture.admin")).thenReturn(admin);
        when(admins.selectById(101L)).thenReturn(admin);
        when(admins.selectRoleCodes(101L)).thenReturn(List.of("HR"));
        when(admins.selectPermissionCodes(101L)).thenReturn(List.of("ADMIN_ATTENDANCE_MANAGE"));
        employee = new AuthUserAccount();
        employee.setId(202L); employee.setUserId(201L); employee.setLoginName("fixture.employee");
        employee.setPasswordHash(hash); employee.setAccountStatus("ACTIVE");
        when(accounts.selectByLoginName("fixture.employee")).thenReturn(employee);
        when(accounts.selectByUserId(201L)).thenReturn(employee);
        org = new OrgUser(); org.setId(201L); org.setRealName("Fixture");
        org.setStatus("ACTIVE"); org.setEmploymentStatus("ACTIVE");
        when(orgs.selectById(201L)).thenReturn(org);
        when(imRepository.existsLoginAllowedOrgUser(201L)).thenReturn(true);
    }

    MockHttpSession login(String type) throws Exception {
        var result = mvc.perform(post("/api/" + type + "/auth/login").with(csrf())
            .contentType("application/json").content("{\"loginName\":\"fixture." + type
                + "\",\"password\":\"Fixture-pass-99\"}"))
            .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    void request(String type, MockHttpSession session, int expected) throws Exception {
        String path = type.equals("admin") ? "/api/admin/attendance/shifts" : "/api/employee/session-probe";
        mvc.perform(get(path).session(session)).andExpect(status().is(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DISABLED", "LOCKED"})
    void employeeAccountRevocationRejectsOldSession(String status) throws Exception {
        var session = login("employee"); request("employee", session, 200);
        employee.setAccountStatus(status);
        request("employee", session, 401);
        assertTrue(session.isInvalid());
        assertThrows(Exception.class, () -> login("employee"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OFFBOARDED", "PENDING_ONBOARD"})
    void employmentRevocationRejectsOldSession(String status) throws Exception {
        var session = login("employee"); request("employee", session, 200);
        org.setEmploymentStatus(status);
        request("employee", session, 401);
        assertTrue(session.isInvalid());
    }

    @Test void inactiveOrgRecordRejectsOldSession() throws Exception {
        var session = login("employee"); org.setStatus("INACTIVE");
        request("employee", session, 401);
    }

    @Test void disabledAdminRejectsOldSession() throws Exception {
        var session = login("admin"); request("admin", session, 200);
        admin.setStatus("INACTIVE"); request("admin", session, 401);
    }

    @Test void permissionRevocationRequiresReloginAndRemainsForbidden() throws Exception {
        var session = login("admin"); request("admin", session, 200);
        when(admins.selectPermissionCodes(101L)).thenReturn(List.of());
        request("admin", session, 401);
        request("admin", login("admin"), 403);
    }

    @Test void roleRevocationEvenWithSamePermissionsInvalidatesSession() throws Exception {
        var session = login("admin");
        when(admins.selectRoleCodes(101L)).thenReturn(List.of());
        request("admin", session, 401);
    }

    @Test void unchangedUsersAndNewGrantRemainUsableAfterRelogin() throws Exception {
        var employeeSession = login("employee"); var adminSession = login("admin");
        request("employee", employeeSession, 200); request("admin", adminSession, 200);
        when(admins.selectPermissionCodes(101L)).thenReturn(List.of("ADMIN_ATTENDANCE_MANAGE", "ADMIN_ORG_MANAGE"));
        request("admin", adminSession, 401);
        request("admin", login("admin"), 200);
        request("employee", employeeSession, 200);
    }

    @Test void missingAccountAndDatabaseFailureNeverUseCachedIdentity() throws Exception {
        var session = login("employee");
        when(accounts.selectByLoginName("fixture.employee")).thenReturn(null);
        request("employee", session, 401);
        when(accounts.selectByLoginName("fixture.employee")).thenReturn(employee);
        session = login("employee");
        when(accounts.selectByLoginName("fixture.employee")).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("fixture unavailable"));
        request("employee", session, 503);
    }

    WebSocketSession socket(MockHttpSession http) throws Exception {
        var attrs = new HashMap<String, Object>();
        var request = new MockHttpServletRequest(); request.setSession(http);
        assertTrue(handshake.beforeHandshake(new ServletServerHttpRequest(request),
            new ServletServerHttpResponse(new MockHttpServletResponse()), handler, attrs));
        var socket = mock(WebSocketSession.class);
        when(socket.getAttributes()).thenReturn(attrs); when(socket.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(socket);
        clearInvocations(socket);
        return socket;
    }

    @ParameterizedTest
    @ValueSource(strings = {"DISABLED", "OFFBOARDED"})
    void establishedSocketCannotReceiveAfterRevocation(String change) throws Exception {
        var socket = socket(login("employee"));
        assertEquals(1, registry.sendToUsers(List.of(201L), "before"));
        clearInvocations(socket);
        if (change.equals("DISABLED")) employee.setAccountStatus(change); else org.setEmploymentStatus(change);
        assertEquals(0, registry.sendToUsers(List.of(201L), "after"));
        verify(socket, never()).sendMessage(any());
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test void establishedSocketCannotExecuteHeartbeatAfterRevocation() throws Exception {
        var socket = socket(login("employee"));
        handler.handleMessage(socket, new TextMessage("PING"));
        verify(socket).sendMessage(any()); clearInvocations(socket);
        employee.setAccountStatus("DISABLED");
        handler.handleMessage(socket, new TextMessage("PING"));
        verify(socket, never()).sendMessage(any()); verify(socket).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test void secondNodeChecksSameDatabaseWithoutLocalInvalidationMessage() throws Exception {
        var session = login("admin");
        // 独立 MVC 请求链重新从 Session 加载；无任何 JVM 会话注册或失效事件。
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(admins.selectPermissionCodes(101L)).thenReturn(List.of());
        request("admin", session, 401);
    }
}
