package com.workforcehub.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.enums.UserType;
import com.workforcehub.common.exception.BusinessErrorCode;
import com.workforcehub.common.response.R;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 后台接口权限校验。
 */
// 权限过滤器：拦截 /api/admin/** 请求，校验用户是否拥有对应路径所需的权限码
@Component
public class AdminPermissionFilter extends OncePerRequestFilter {

    private static final String ADMIN_PREFIX = "/api/admin/";
    private static final List<PathPermission> PATH_PERMISSIONS = List.of(
        new PathPermission("/api/admin/rbac/", "ADMIN_RBAC_MANAGE"),
        new PathPermission("/api/admin/departments", "ADMIN_ORG_MANAGE"),
        new PathPermission("/api/admin/positions", "ADMIN_ORG_MANAGE"),
        new PathPermission("/api/admin/users", "ADMIN_ORG_MANAGE"),
        new PathPermission("/api/admin/attendance/", "ADMIN_ATTENDANCE_MANAGE"),
        new PathPermission("/api/admin/leave/", "ADMIN_LEAVE_MANAGE"),
        new PathPermission("/api/admin/workflow/", "ADMIN_WORKFLOW_MANAGE"),
        new PathPermission("/api/admin/agent-tasks", "ADMIN_AGENT_TASK_MANAGE"),
        new PathPermission("/api/admin/agents/", "ADMIN_AGENT_MANAGE"),
        new PathPermission("/api/admin/files/", "ADMIN_FILE_MANAGE"),
        new PathPermission("/api/admin/ragflow/", "ADMIN_RAGFLOW_MANAGE")
    );

    private final ObjectMapper objectMapper;

    public AdminPermissionFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith(ADMIN_PREFIX) || uri.startsWith("/api/admin/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String requiredPermission = requiredPermission(uri);
        if (requiredPermission == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof WfhUserDetails user)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (auth.isAuthenticated()
            && user.getUserType() == UserType.ADMIN
            && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(requiredPermission::equals)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), R.fail(BusinessErrorCode.FORBIDDEN.code(), "无权限访问"));
    }

    private String requiredPermission(String uri) {
        return PATH_PERMISSIONS.stream()
            .filter(item -> uri.startsWith(item.pathPrefix()))
            .map(PathPermission::permissionCode)
            .findFirst()
            .orElse(null);
    }

    private record PathPermission(String pathPrefix, String permissionCode) {}
}
