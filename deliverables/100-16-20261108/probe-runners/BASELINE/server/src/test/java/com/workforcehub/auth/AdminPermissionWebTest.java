package com.workforcehub.auth;

import com.workforcehub.auth.controller.AdminAuthController;
import com.workforcehub.auth.dto.CurrentUserResponse;
import com.workforcehub.auth.security.AdminPermissionFilter;
import com.workforcehub.auth.security.AdminUserDetails;
import com.workforcehub.auth.security.AdminUserDetailsService;
import com.workforcehub.auth.security.EmployeeUserDetails;
import com.workforcehub.auth.security.EmployeeUserDetailsService;
import com.workforcehub.auth.security.UserTypePathFilter;
import com.workforcehub.auth.security.SessionValidityFilter;
import com.workforcehub.auth.security.SessionValidityService;
import com.workforcehub.auth.service.AccountService;
import com.workforcehub.auth.service.LoginService;
import com.workforcehub.common.config.SecurityConfig;
import com.workforcehub.im.controller.AdminImOutboxController;
import com.workforcehub.im.dto.AdminImOutboxDispatchResponse;
import com.workforcehub.im.service.ImOutboxAdminService;
import com.workforcehub.system.controller.AdminAlertController;
import com.workforcehub.system.controller.AdminSystemController;
import com.workforcehub.system.service.AdminAlertService;
import com.workforcehub.system.service.AdminSystemService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminAlertController.class, AdminImOutboxController.class, AdminSystemController.class,
    AdminAuthController.class})
@ContextConfiguration(classes = {AdminAlertController.class, AdminImOutboxController.class,
    AdminSystemController.class, AdminAuthController.class, SecurityConfig.class,
    UserTypePathFilter.class, AdminPermissionFilter.class, SessionValidityFilter.class})
class AdminPermissionWebTest {

    @Autowired private MockMvc mvc;
    @MockBean private AdminAlertService alerts;
    @MockBean private ImOutboxAdminService outbox;
    @MockBean private AdminSystemService system;
    @MockBean private LoginService login;
    @MockBean private AccountService account;
    @MockBean(name = "employeeUserDetailsService") private EmployeeUserDetailsService employees;
    @MockBean(name = "adminUserDetailsService") private AdminUserDetailsService admins;
    @MockBean private SessionValidityService sessionValidity;

    @BeforeEach
    void keepSessionValidationOutsideRoutePermissionSlice() {
        when(sessionValidity.isCurrent(org.mockito.ArgumentMatchers.any())).thenReturn(true);
    }

    record Endpoint(String method, String path, String permission, String body, String serviceMethod) {}

    static Stream<Endpoint> previouslyUnmappedEndpoints() {
        return Stream.of(
            new Endpoint("GET", "/api/admin/alerts", "ADMIN_ALERT_MANAGE", "", "listActiveAlerts"),
            new Endpoint("GET", "/api/admin/alerts/channels", "ADMIN_ALERT_MANAGE", "", "listChannels"),
            new Endpoint("POST", "/api/admin/alerts/channels", "ADMIN_ALERT_MANAGE",
                "{\"channelCode\":\"FIXTURE\",\"channelName\":\"fixture\",\"channelType\":\"IN_APP\"}", "createChannel"),
            new Endpoint("PUT", "/api/admin/alerts/rules/FIXTURE", "ADMIN_ALERT_MANAGE",
                "{\"enabled\":true,\"notifyEnabled\":false,\"cooldownMinutes\":0}", "updateRule"),
            new Endpoint("POST", "/api/admin/alerts/FIXTURE/resolutions", "ADMIN_ALERT_MANAGE",
                "{\"actionType\":\"RESOLVE\"}", "createResolution"),
            new Endpoint("GET", "/api/admin/alerts/resolutions", "ADMIN_ALERT_MANAGE", "", "pageResolutions"),
            new Endpoint("GET", "/api/admin/im/outbox-events", "ADMIN_IM_OUTBOX_MANAGE", "", "pageOutboxEvents"),
            new Endpoint("GET", "/api/admin/im/outbox-events/summary", "ADMIN_IM_OUTBOX_MANAGE", "", "summarizeOutboxEvents"),
            new Endpoint("GET", "/api/admin/im/outbox-events/failure-summary", "ADMIN_IM_OUTBOX_MANAGE", "", "listOutboxFailureSummary"),
            new Endpoint("GET", "/api/admin/im/outbox-events/audits", "ADMIN_IM_OUTBOX_MANAGE", "", "pageOutboxAudits"),
            new Endpoint("POST", "/api/admin/im/outbox-events/batch-retry", "ADMIN_IM_OUTBOX_MANAGE",
                "{\"eventIds\":[1]}", "batchRetryOutboxEvents"),
            new Endpoint("POST", "/api/admin/im/outbox-events/dispatch", "ADMIN_IM_OUTBOX_MANAGE",
                "{\"batchSize\":7}", "dispatchDueEvents"),
            new Endpoint("GET", "/api/admin/im/outbox-events/1", "ADMIN_IM_OUTBOX_MANAGE", "", "getOutboxEvent"),
            new Endpoint("POST", "/api/admin/im/outbox-events/1/retry", "ADMIN_IM_OUTBOX_MANAGE", "", "retryOutboxEvent"),
            new Endpoint("GET", "/api/admin/system/roles", "ADMIN_RBAC_MANAGE", "", "listRoles"),
            new Endpoint("GET", "/api/admin/roles", "ADMIN_RBAC_MANAGE", "", "listRoles"),
            new Endpoint("GET", "/api/admin/system/permissions", "ADMIN_RBAC_MANAGE", "", "listPermissions"),
            new Endpoint("GET", "/api/admin/permissions", "ADMIN_RBAC_MANAGE", "", "listPermissions"),
            new Endpoint("GET", "/api/admin/system/audit-logs", "ADMIN_AUDIT_LOG_VIEW", "", "pageAuditLogs"),
            new Endpoint("GET", "/api/admin/audit-logs", "ADMIN_AUDIT_LOG_VIEW", "", "pageAuditLogs")
        );
    }

    @ParameterizedTest
    @MethodSource("previouslyUnmappedEndpoints")
    void deniedRequestsMustNeverReachBusinessAndExplicitPermissionMustReachExpectedHandler(Endpoint endpoint)
            throws Exception {
        Object service = endpoint.path().contains("/alerts") ? alerts
            : endpoint.path().contains("/im/") ? outbox : system;
        Class<?> controller = service == alerts ? AdminAlertController.class
            : service == outbox ? AdminImOutboxController.class : AdminSystemController.class;
        for (List<String> permissions : List.of(List.<String>of(), List.of("UNRELATED_PERMISSION"))) {
            mvc.perform(request(HttpMethod.valueOf(endpoint.method()), endpoint.path())
                    .with(admin(List.of(), permissions)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(endpoint.body()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(100403));
            verifyNoInteractions(alerts, outbox, system);
        }
        for (List<String> roles : List.of(List.<String>of(), List.of("SUPER_ADMIN"))) {
            mvc.perform(request(HttpMethod.valueOf(endpoint.method()), endpoint.path())
                    .with(admin(roles, roles.isEmpty() ? List.of(endpoint.permission()) : List.of())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(endpoint.body()))
                .andExpect(status().isOk()).andExpect(handler().handlerType(controller))
                .andExpect(jsonPath("$.code").value(0));
            assertEquals(1, mockingDetails(service).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals(endpoint.serviceMethod())).count());
            clearInvocations(alerts, outbox, system);
        }
    }

    @Test
    void dispatchResponseMustComeFromRealControllerAndExplicitServiceInvocation() throws Exception {
        when(outbox.dispatchDueEvents(7)).thenReturn(AdminImOutboxDispatchResponse.of(7, 3));
        mvc.perform(post("/api/admin/im/outbox-events/dispatch").with(csrf())
                .with(admin(List.of(), List.of("ADMIN_IM_OUTBOX_MANAGE")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"batchSize\":7}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.requestedBatchSize").value(7))
            .andExpect(jsonPath("$.data.dispatchedCount").value(3));
        verify(outbox).dispatchDueEvents(7);
    }

    @Test
    void unknownRouteAndWrongMethodMustBeDeniedEvenForSuperAdmin() throws Exception {
        mvc.perform(get("/api/admin/new-business").with(admin(List.of("SUPER_ADMIN"), List.of())))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/system/audit-logs").with(csrf())
                .with(admin(List.of("SUPER_ADMIN"), List.of())))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/auth/new-business").with(admin(List.of("SUPER_ADMIN"), List.of())))
            .andExpect(status().isForbidden());
        verifyNoInteractions(alerts, outbox, system, account, login);
    }

    @Test
    void anonymousMustRemain401AndEmployeeMustRemain403() throws Exception {
        mvc.perform(get("/api/admin/alerts")).andExpect(status().isUnauthorized());
        var employee = new EmployeeUserDetails(2L, 2L, "fixture", "unused", "fixture", "ACTIVE");
        mvc.perform(get("/api/admin/auth/me").with(authentication(
                new UsernamePasswordAuthenticationToken(employee, null, employee.getAuthorities()))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(alerts, outbox, system, account, login);
    }

    @Test
    void authenticatedAdminMustAccessOnlyExplicitCommonEndpoints() throws Exception {
        var current = new CurrentUserResponse();
        current.setDisplayName("fixture-current-admin");
        when(account.getCurrentUser()).thenReturn(current);
        mvc.perform(get("/api/admin/auth/me").with(admin(List.of(), List.of())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.displayName").value("fixture-current-admin"));
        verify(account).getCurrentUser();
        mvc.perform(post("/api/admin/auth/logout").with(admin(List.of(), List.of())).with(csrf()))
            .andExpect(status().isOk());
        verify(login).logout(org.mockito.ArgumentMatchers.any());
        mvc.perform(get("/api/admin/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/auth/logout").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousLoginMustReachExistingControllerWithCsrf() throws Exception {
        mvc.perform(post("/api/admin/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginName\":\"fixture\",\"password\":\"fixture-password\"}"))
            .andExpect(status().isOk()).andExpect(handler().handlerType(AdminAuthController.class));
        verify(login).loginAdmin(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private RequestPostProcessor admin(List<String> roles, List<String> permissions) {
        var user = new AdminUserDetails(1L, "fixture", "unused", "fixture", "ACTIVE", roles, permissions);
        return authentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
