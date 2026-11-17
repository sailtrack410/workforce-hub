package com.workforcehub.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.auth.service.AdminPermissionService;
import com.workforcehub.common.enums.UserType;
import com.workforcehub.common.exception.BusinessErrorCode;
import com.workforcehub.common.response.R;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.workforcehub.common.config.RequestPathResolver.resolve;

/**
 * 后台接口权限校验。
 */
// 权限过滤器：拦截 /api/admin/** 请求，校验用户是否拥有对应路径所需的权限码
@Component
public class AdminPermissionFilter extends OncePerRequestFilter {

    private static final String ADMIN_PREFIX = "/api/admin/";
    // 只授权已声明的方法和路由，避免新增接口继承宽泛前缀而静默放行。
    private static final List<PathPermission> PATH_PERMISSIONS = java.util.stream.Stream.of(
        paths("ADMIN_AGENT_MANAGE", "GET",
            "/api/admin/agents",
            "/api/admin/agents/{agentId}",
            "/api/admin/agents/{agentId}/versions",
            "/api/admin/agents/tools",
            "/api/admin/agents/{agentId}/acl",
            "/api/admin/agents/ai-providers",
            "/api/admin/agents/ai-providers/{providerId}/models",
            "/api/admin/agents/llm-models"),
        paths("ADMIN_AGENT_MANAGE", "POST",
            "/api/admin/agents",
            "/api/admin/agents/workflows/validate",
            "/api/admin/agents/{agentId}/versions",
            "/api/admin/agents/tools",
            "/api/admin/agents/versions/{versionId}/tools",
            "/api/admin/agents/{agentId}/acl",
            "/api/admin/agents/versions/{versionId}/publish",
            "/api/admin/agents/{agentId}/disable",
            "/api/admin/agents/{agentId}/enable",
            "/api/admin/agents/ai-providers",
            "/api/admin/agents/ai-providers/{providerId}/test",
            "/api/admin/agents/llm-models",
            "/api/admin/agents/llm-models/test",
            "/api/admin/agents/llm-models/{modelId}/test",
            "/api/admin/agents/trial-run",
            "/api/admin/agents/workflow-run"),
        paths("ADMIN_AGENT_MANAGE", "PUT",
            "/api/admin/agents/{agentId}",
            "/api/admin/agents/versions/{versionId}",
            "/api/admin/agents/ai-providers/{providerId}",
            "/api/admin/agents/llm-models/{modelId}"),
        paths("ADMIN_AGENT_MANAGE", "DELETE",
            "/api/admin/agents/{agentId}",
            "/api/admin/agents/ai-providers/{providerId}",
            "/api/admin/agents/llm-models/{modelId}"),
        paths("ADMIN_AGENT_TASK_MANAGE", "GET",
            "/api/admin/agent-tasks",
            "/api/admin/agent-tasks/metrics",
            "/api/admin/agent-tasks/failure-summary",
            "/api/admin/agent-tasks/timeout-scan-runs",
            "/api/admin/agent-tasks/{taskId}",
            "/api/admin/agent-tasks/{taskId}/tool-calls",
            "/api/admin/agent-tasks/{taskId}/audits"),
        paths("ADMIN_AGENT_TASK_MANAGE", "POST",
            "/api/admin/agent-tasks/timeout-scan"),
        paths("ADMIN_ATTENDANCE_MANAGE", "POST",
            "/api/admin/attendance/calendars",
            "/api/admin/attendance/shifts",
            "/api/admin/attendance/groups",
            "/api/admin/attendance/groups/{groupId}/members",
            "/api/admin/attendance/schedules"),
        paths("ADMIN_ATTENDANCE_MANAGE", "GET",
            "/api/admin/attendance/calendars",
            "/api/admin/attendance/shifts",
            "/api/admin/attendance/groups",
            "/api/admin/attendance/groups/{groupId}/members",
            "/api/admin/attendance/schedules",
            "/api/admin/attendance/daily-results"),
        paths("ADMIN_ATTENDANCE_MANAGE", "PUT",
            "/api/admin/attendance/schedules/{scheduleId}"),
        paths("ADMIN_RBAC_MANAGE", "GET",
            "/api/admin/rbac/permissions",
            "/api/admin/rbac/roles",
            "/api/admin/rbac/roles/{roleId}",
            "/api/admin/rbac/admin-users",
            "/api/admin/rbac/admin-users/{adminUserId}/roles",
            "/api/admin/system/roles",
            "/api/admin/roles",
            "/api/admin/system/permissions",
            "/api/admin/permissions"),
        paths("ADMIN_RBAC_MANAGE", "POST",
            "/api/admin/rbac/roles"),
        paths("ADMIN_RBAC_MANAGE", "PUT",
            "/api/admin/rbac/roles/{roleId}",
            "/api/admin/rbac/roles/{roleId}/permissions",
            "/api/admin/rbac/admin-users/{adminUserId}/roles"),
        paths("ADMIN_RBAC_MANAGE", "DELETE",
            "/api/admin/rbac/roles/{roleId}"),
        paths("ADMIN_FILE_MANAGE", "GET",
            "/api/admin/files",
            "/api/admin/files/recycle-bin",
            "/api/admin/files/storage-summary",
            "/api/admin/files/download/{ticketNo}",
            "/api/admin/files/quota-policies"),
        paths("ADMIN_FILE_MANAGE", "POST",
            "/api/admin/files/upload-tickets",
            "/api/admin/files/confirm-upload",
            "/api/admin/files/{attachmentRefId}/download-tickets",
            "/api/admin/files/{attachmentRefId}/trash",
            "/api/admin/files/{attachmentRefId}/restore",
            "/api/admin/files/cleanup-jobs/run",
            "/api/admin/files/quota-policies"),
        paths("ADMIN_FILE_MANAGE", "DELETE",
            "/api/admin/files/{attachmentRefId}"),
        paths("ADMIN_FILE_MANAGE", "PUT",
            "/api/admin/files/quota-policies/{policyId}"),
        paths("ADMIN_IM_OUTBOX_MANAGE", "GET",
            "/api/admin/im/outbox-events",
            "/api/admin/im/outbox-events/summary",
            "/api/admin/im/outbox-events/failure-summary",
            "/api/admin/im/outbox-events/audits",
            "/api/admin/im/outbox-events/{eventId}"),
        paths("ADMIN_IM_OUTBOX_MANAGE", "POST",
            "/api/admin/im/outbox-events/batch-retry",
            "/api/admin/im/outbox-events/dispatch",
            "/api/admin/im/outbox-events/{eventId}/retry"),
        paths("ADMIN_LEAVE_MANAGE", "GET",
            "/api/admin/leave/types",
            "/api/admin/leave/accounts"),
        paths("ADMIN_LEAVE_MANAGE", "POST",
            "/api/admin/leave/types",
            "/api/admin/leave/accounts/adjustments"),
        paths("ADMIN_LEAVE_MANAGE", "PUT",
            "/api/admin/leave/types/{leaveTypeId}"),
        paths("ADMIN_LEAVE_MANAGE", "DELETE",
            "/api/admin/leave/types/{leaveTypeId}"),
        paths("ADMIN_ORG_MANAGE", "GET",
            "/api/admin/departments/tree",
            "/api/admin/positions",
            "/api/admin/users",
            "/api/admin/users/org-chart",
            "/api/admin/users/{userId}",
            "/api/admin/users/{userId}/manager",
            "/api/admin/users/{userId}/subordinates"),
        paths("ADMIN_ORG_MANAGE", "POST",
            "/api/admin/departments",
            "/api/admin/positions",
            "/api/admin/users"),
        paths("ADMIN_ORG_MANAGE", "PUT",
            "/api/admin/departments/{deptId}",
            "/api/admin/positions/{positionId}",
            "/api/admin/users/{userId}",
            "/api/admin/users/{userId}/employment-status",
            "/api/admin/users/{userId}/account"),
        paths("ADMIN_ORG_MANAGE", "DELETE",
            "/api/admin/departments/{deptId}"),
        paths("ADMIN_PLUGIN_MANAGE", "GET",
            "/api/admin/plugins/{pluginId}/config",
            "/api/admin/plugins"),
        paths("ADMIN_PLUGIN_MANAGE", "PUT",
            "/api/admin/plugins/{pluginId}/config/{configKey}",
            "/api/admin/plugins/{pluginId}/enable",
            "/api/admin/plugins/{pluginId}/disable"),
        paths("ADMIN_PLUGIN_MANAGE", "DELETE",
            "/api/admin/plugins/{pluginId}/config/{configKey}",
            "/api/admin/plugins/{pluginId}"),
        paths("ADMIN_PLUGIN_MANAGE", "POST",
            "/api/admin/plugins/upload"),
        paths("ADMIN_RAGFLOW_MANAGE", "GET",
            "/api/admin/ragflow/settings",
            "/api/admin/ragflow/spaces",
            "/api/admin/ragflow/spaces/{spaceId}",
            "/api/admin/ragflow/spaces/{spaceId}/acl",
            "/api/admin/ragflow/spaces/{spaceId}/documents"),
        paths("ADMIN_RAGFLOW_MANAGE", "PUT",
            "/api/admin/ragflow/settings",
            "/api/admin/ragflow/spaces/{spaceId}",
            "/api/admin/ragflow/spaces/{spaceId}/acl"),
        paths("ADMIN_RAGFLOW_MANAGE", "POST",
            "/api/admin/ragflow/spaces",
            "/api/admin/ragflow/files/upload-tickets",
            "/api/admin/ragflow/files/confirm-upload",
            "/api/admin/ragflow/spaces/{spaceId}/documents",
            "/api/admin/ragflow/spaces/{spaceId}/documents/{documentId}/reingest",
            "/api/admin/ragflow/spaces/{spaceId}/search-test"),
        paths("ADMIN_RAGFLOW_MANAGE", "DELETE",
            "/api/admin/ragflow/spaces/{spaceId}",
            "/api/admin/ragflow/spaces/{spaceId}/documents/{documentId}"),
        paths("ADMIN_ALERT_MANAGE", "GET",
            "/api/admin/alerts",
            "/api/admin/alerts/channels",
            "/api/admin/alerts/resolutions"),
        paths("ADMIN_ALERT_MANAGE", "POST",
            "/api/admin/alerts/channels",
            "/api/admin/alerts/{alertCode}/resolutions"),
        paths("ADMIN_ALERT_MANAGE", "PUT",
            "/api/admin/alerts/rules/{alertCode}"),
        paths("ADMIN_DICT_MANAGE", "GET",
            "/api/admin/dicts/types",
            "/api/admin/dicts"),
        paths("ADMIN_DICT_MANAGE", "POST",
            "/api/admin/dicts"),
        paths("ADMIN_DICT_MANAGE", "PUT",
            "/api/admin/dicts/{id}"),
        paths("ADMIN_DICT_MANAGE", "DELETE",
            "/api/admin/dicts/{id}"),
        paths("ADMIN_NOTIFICATION_CHANNEL_MANAGE", "GET",
            "/api/admin/notification-channels",
            "/api/admin/notification-channels/{channelCode}"),
        paths("ADMIN_NOTIFICATION_CHANNEL_MANAGE", "PUT",
            "/api/admin/notification-channels/{channelCode}"),
        paths("ADMIN_NOTIFICATION_CHANNEL_MANAGE", "POST",
            "/api/admin/notification-channels/{channelCode}/test"),
        paths("ADMIN_AUDIT_LOG_VIEW", "GET",
            "/api/admin/system/audit-logs",
            "/api/admin/audit-logs"),
        paths("ADMIN_WORKFLOW_MANAGE", "POST",
            "/api/admin/workflow/process-types",
            "/api/admin/workflow/templates",
            "/api/admin/workflow/templates/{templateId}/versions",
            "/api/admin/workflow/template-versions/{versionId}/publish"),
        paths("ADMIN_WORKFLOW_MANAGE", "GET",
            "/api/admin/workflow/requests",
            "/api/admin/workflow/requests/{requestId}",
            "/api/admin/workflow/requests/{requestId}/actions",
            "/api/admin/workflow/process-types",
            "/api/admin/workflow/templates",
            "/api/admin/workflow/templates/{templateId}/versions")
    ).flatMap(List::stream).toList();

    private final ObjectMapper objectMapper;

    public AdminPermissionFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = resolve(request);
        if (!(uri.equals("/api/admin") || uri.startsWith(ADMIN_PREFIX))
            || ("POST".equals(request.getMethod()) && uri.equals("/api/admin/auth/login"))) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // 未登录请求交由 SecurityConfig 的认证入口统一返回 401。
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }
        String requiredPermission = requiredPermission(request.getMethod(), uri);
        boolean authenticatedEndpoint = (("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
                && uri.equals("/api/admin/auth/me"))
            || ("POST".equals(request.getMethod()) && uri.equals("/api/admin/auth/logout"));
        Set<String> authorities = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(java.util.stream.Collectors.toSet());
        boolean superAdmin = auth.getPrincipal() instanceof AdminUserDetails admin
            && AdminPermissionService.hasSuperAdminRole(admin.getRoleCodes());
        if (auth.isAuthenticated()
            && auth.getPrincipal() instanceof WfhUserDetails user
            && user.getUserType() == UserType.ADMIN
            && (authenticatedEndpoint
                || (requiredPermission != null && (authorities.contains(requiredPermission) || superAdmin)))) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), R.fail(BusinessErrorCode.FORBIDDEN.code(), "无权限访问"));
    }

    private String requiredPermission(String method, String uri) {
        // Spring MVC 的 GET 路由也接受 HEAD；其余方法必须显式声明。
        String effectiveMethod = "HEAD".equals(method) ? "GET" : method;
        PathContainer path = PathContainer.parsePath(uri, PathContainer.Options.create('/', false));
        return PATH_PERMISSIONS.stream()
            .filter(item -> item.method().equals(effectiveMethod) && item.pattern().matches(path))
            .map(PathPermission::permissionCode)
            .findFirst()
            .orElse(null);
    }

    private static List<PathPermission> paths(String permission, String method, String... patterns) {
        return Arrays.stream(patterns)
            .map(pattern -> new PathPermission(method, PathPatternParser.defaultInstance.parse(pattern), permission))
            .toList();
    }

    private record PathPermission(String method, PathPattern pattern, String permissionCode) {}
}
