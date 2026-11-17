package com.workforcehub.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.auth.security.AdminPermissionFilter;
import com.workforcehub.auth.security.AdminUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminPermissionFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void superAdminRoleShouldPassProtectedAdminApiWithoutPermissionCodes() throws Exception {
        AdminPermissionFilter filter = new AdminPermissionFilter(new ObjectMapper());
        AdminUserDetails user = new AdminUserDetails(1L, "admin.demo", "password", "管理员", "ACTIVE",
            List.of("SUPER_ADMIN"), List.of());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/attendance/shifts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> called.set(true));

        assertTrue(called.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void pluginManagePermissionShouldProtectPluginConfigApi() throws Exception {
        AdminPermissionFilter filter = new AdminPermissionFilter(new ObjectMapper());
        authenticate(List.of(), List.of());
        MockHttpServletRequest request = new MockHttpServletRequest(
            "GET", "/api/admin/plugins/plugin-a/config");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> called.set(true));

        assertFalse(called.get());
        assertEquals(403, response.getStatus());
    }

    @Test
    void encodedAdminAndPluginSegmentsShouldRemainProtected() throws Exception {
        AdminPermissionFilter filter = new AdminPermissionFilter(new ObjectMapper());
        authenticate(List.of(), List.of());
        MockHttpServletRequest request = new MockHttpServletRequest(
            "GET", "/api/%61dmin/%70lugins/plugin-a/config");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> called.set(true));

        assertFalse(called.get());
        assertEquals(403, response.getStatus());
    }

    @Test
    void pluginManagePermissionShouldAllowAuthorizedAdmin() throws Exception {
        AdminPermissionFilter filter = new AdminPermissionFilter(new ObjectMapper());
        authenticate(List.of(), List.of("ADMIN_PLUGIN_MANAGE"));
        MockHttpServletRequest request = new MockHttpServletRequest(
            "PUT", "/api/admin/plugins/plugin-a/config/feature.enabled");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> called.set(true));

        assertTrue(called.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void similarPrefixShouldNotBeTreatedAsPluginApi() throws Exception {
        AdminPermissionFilter filter = new AdminPermissionFilter(new ObjectMapper());
        authenticate(List.of(), List.of());
        MockHttpServletRequest request = new MockHttpServletRequest(
            "GET", "/api/admin/plugins-evil/config");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> called.set(true));

        assertTrue(called.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void exactBasePathShouldRemainProtectedWhenConfiguredPrefixHasTrailingSlash() throws Exception {
        AdminPermissionFilter filter = new AdminPermissionFilter(new ObjectMapper());
        authenticate(List.of(), List.of());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/agents");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> called.set(true));

        assertFalse(called.get());
        assertEquals(403, response.getStatus());
    }

    private void authenticate(List<String> roleCodes, List<String> permissionCodes) {
        AdminUserDetails user = new AdminUserDetails(
            1L, "admin.demo", "password", "管理员", "ACTIVE", roleCodes, permissionCodes);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );
    }
}
