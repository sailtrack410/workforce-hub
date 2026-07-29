package com.workforcehub.plugin;

import com.workforcehub.auth.security.AdminPermissionFilter;
import com.workforcehub.auth.security.AdminUserDetails;
import com.workforcehub.auth.security.AdminUserDetailsService;
import com.workforcehub.auth.security.EmployeeUserDetailsService;
import com.workforcehub.auth.security.UserTypePathFilter;
import com.workforcehub.auth.security.WfhUserDetails;
import com.workforcehub.common.config.SecurityConfig;
import com.workforcehub.common.enums.UserType;
import com.workforcehub.plugin.controller.admin.AdminPluginController;
import com.workforcehub.plugin.dto.AdminPluginResponse;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.runtime.AdminPluginManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPluginController.class)
@ContextConfiguration(classes = {
    AdminPluginController.class,
    SecurityConfig.class,
    UserTypePathFilter.class,
    AdminPermissionFilter.class
})
class AdminPluginManagementWebTest {

    private static final String PLUGIN_ID = "hello-plugin";
    private static final String MANAGE_PERMISSION = "ADMIN_PLUGIN_MANAGE";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminPluginManagementService service;

    @MockBean(name = "employeeUserDetailsService")
    private EmployeeUserDetailsService employeeUserDetailsService;

    @MockBean(name = "adminUserDetailsService")
    private AdminUserDetailsService adminUserDetailsService;

    @Test
    void anonymousShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/plugins"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void employeeShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/admin/plugins").with(employee()))
            .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void adminWithoutPermissionShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/admin/plugins").with(admin(List.of())))
            .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void authorizedAdminShouldListPluginsWithoutInternalFields() throws Exception {
        when(service.list()).thenReturn(List.of(pluginResponse()));

        mockMvc.perform(get("/api/admin/plugins").with(authorizedAdmin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].id").value(PLUGIN_ID))
            .andExpect(jsonPath("$.data[0].status").value("INSTALLED"))
            .andExpect(jsonPath("$.data[0].jarPath").doesNotExist())
            .andExpect(jsonPath("$.data[0].jarSha256").doesNotExist())
            .andExpect(jsonPath("$.data[0].signature").doesNotExist())
            .andExpect(jsonPath("$.data[0].signerId").doesNotExist())
            .andExpect(jsonPath("$.data[0].integrity").doesNotExist())
            .andExpect(jsonPath("$.data[0].errorMessage").doesNotExist());

        verify(service).list();
    }

    @Test
    void stateChangingRequestsWithoutCsrfShouldReturn403() throws Exception {
        mockMvc.perform(put("/api/admin/plugins/{pluginId}/enable", PLUGIN_ID)
                .with(authorizedAdmin()))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/plugins/{pluginId}/disable", PLUGIN_ID)
                .with(authorizedAdmin()))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/plugins/{pluginId}", PLUGIN_ID)
                .with(authorizedAdmin()))
            .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void authorizedAdminWithCsrfShouldInvokeLifecycleOperations() throws Exception {
        mockMvc.perform(put("/api/admin/plugins/{pluginId}/enable", PLUGIN_ID)
                .with(authorizedAdmin())
                .with(csrf().asHeader()))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/plugins/{pluginId}/disable", PLUGIN_ID)
                .with(authorizedAdmin())
                .with(csrf().asHeader()))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/plugins/{pluginId}", PLUGIN_ID)
                .with(authorizedAdmin())
                .with(csrf().asHeader()))
            .andExpect(status().isOk());

        verify(service).enable(PLUGIN_ID);
        verify(service).disable(PLUGIN_ID);
        verify(service).uninstall(PLUGIN_ID);
    }

    private AdminPluginResponse pluginResponse() {
        LocalDateTime installedAt = LocalDateTime.of(2026, 7, 30, 10, 0);
        return new AdminPluginResponse(
            PLUGIN_ID,
            "Hello Plugin",
            "测试插件",
            "1.0.0",
            "0.1.0",
            "0.1.0",
            "0.2.0",
            "17",
            "FUNCTIONAL",
            PluginStatus.INSTALLED,
            null,
            "Workforce Hub",
            installedAt,
            installedAt
        );
    }

    private RequestPostProcessor authorizedAdmin() {
        return admin(List.of(MANAGE_PERMISSION));
    }

    private RequestPostProcessor admin(List<String> permissions) {
        AdminUserDetails principal = new AdminUserDetails(
            1L,
            "plugin.admin",
            "password",
            "插件管理员",
            "ACTIVE",
            List.of(),
            permissions
        );
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            principal.getAuthorities()
        );
        return authentication(authentication);
    }

    private RequestPostProcessor employee() {
        WfhUserDetails principal = org.mockito.Mockito.mock(WfhUserDetails.class);
        when(principal.getUserType()).thenReturn(UserType.USER);
        when(principal.getUserId()).thenReturn(2L);
        when(principal.getUsername()).thenReturn("employee");
        when(principal.getAuthorities()).thenReturn(List.of());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            principal.getAuthorities()
        );
        return authentication(authentication);
    }
}
