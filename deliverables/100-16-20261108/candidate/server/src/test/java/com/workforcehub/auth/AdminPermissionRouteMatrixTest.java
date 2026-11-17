package com.workforcehub.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.auth.security.AdminPermissionFilter;
import com.workforcehub.auth.security.AdminUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AdminPermissionRouteMatrixTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private List<String> matrix() throws Exception {
        return new ClassPathResource("security/admin-permission-matrix.tsv")
            .getContentAsString(StandardCharsets.UTF_8).lines()
            .filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
    }

    @Test
    void matrixMustExactlyCoverEveryActualAdminRouteAndMethod() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        var introspector = new MappingIntrospector();
        Set<String> actual = new HashSet<>();
        for (var bean : scanner.findCandidateComponents("com.workforcehub")) {
            Class<?> type = Class.forName(bean.getBeanClassName());
            for (Method method : type.getDeclaredMethods()) {
                RequestMappingInfo info = introspector.mapping(method, type);
                if (info == null) {
                    continue;
                }
                for (String path : info.getPatternValues()) {
                    if (path.equals("/api/admin") || path.startsWith("/api/admin/")) {
                        assertFalse(info.getMethodsCondition().isEmpty(), "Explicit HTTP method required: " + path);
                        for (var verb : info.getMethodsCondition().getMethods()) {
                            actual.add(verb.name() + " " + path);
                        }
                    }
                }
            }
        }
        List<String> rows = matrix();
        Set<String> declared = new HashSet<>();
        rows.forEach(row -> declared.add(row.substring(0, row.lastIndexOf('\t')).replace('\t', ' ')));
        assertEquals(rows.size(), declared.size(), "Duplicate matrix route");
        assertEquals(actual, declared, "Every new/changed admin mapping needs an explicit matrix policy");
        System.out.println("Admin route matrix: " + actual.size() + " method/path mappings covered");
    }

    @TestFactory
    Stream<DynamicTest> everyRouteMustEnforceItsDeclaredPermission() throws Exception {
        return matrix().stream().map(row -> DynamicTest.dynamicTest(row, () -> {
            String[] fields = row.split("\t");
            String method = fields[0];
            String path = fields[1].replaceAll("\\{[^}]+}", "123");
            String policy = fields[2];
            boolean common = policy.equals("LOGIN") || policy.equals("AUTHENTICATED");
            assertAccess(method, path, List.of(), List.of(), common);
            assertAccess(method, path, List.of(), List.of("UNRELATED_PERMISSION"), common);
            assertAccess(method, path, List.of(), List.of(policy), true);
            assertAccess(method, path, List.of("SUPER_ADMIN"), List.of(), true);
            if (method.equals("GET")) {
                assertAccess("HEAD", path, List.of(), List.of(), common);
                assertAccess("HEAD", path, List.of(), List.of(policy), true);
            }
            assertAccess("TRACE", path, List.of("SUPER_ADMIN"), List.of(policy), false);
        }));
    }

    @Test
    void undeclaredRoutesAndMethodsMustNotInheritAnyBypass() throws Exception {
        for (String path : List.of("/api/admin", "/api/admin/unknown", "/api/admin/auth/new-action",
                "/api/admin/plugins-evil/config", "/api/admin/alerts/unknown/deep",
                "/api/admin/system/audit-logs/unknown")) {
            assertAccess("GET", path, List.of(), List.of(), false);
            assertAccess("GET", path, List.of("SUPER_ADMIN"), List.of(), false);
        }
        assertAccess("POST", "/api/admin/auth/me", List.of("SUPER_ADMIN"), List.of(), false);
        assertAccess("DELETE", "/api/admin/alerts", List.of(), List.of("ADMIN_ALERT_MANAGE"), false);
        assertAccess("GET", "/api/%61dmin/alerts", List.of(), List.of(), false);
        assertAccess("GET", "/api/admin/system/audit-logs;fixture=1", List.of(), List.of(), false);
    }

    private void assertAccess(String method, String path, List<String> roles, List<String> permissions,
                              boolean expected) throws Exception {
        var user = new AdminUserDetails(1L, "fixture", "unused", "fixture", "ACTIVE", roles, permissions);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();
        new AdminPermissionFilter(new ObjectMapper()).doFilter(new MockHttpServletRequest(method, path), response,
            (request, result) -> called.set(true));
        assertEquals(expected, called.get(), method + " " + path + " roles=" + roles + " permissions=" + permissions);
        if (!expected) {
            assertEquals(403, response.getStatus());
        }
    }

    private static class MappingIntrospector extends RequestMappingHandlerMapping {
        RequestMappingInfo mapping(Method method, Class<?> type) {
            return getMappingForMethod(method, type);
        }
    }
}
