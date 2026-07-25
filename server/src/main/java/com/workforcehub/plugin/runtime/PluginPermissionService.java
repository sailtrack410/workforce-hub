package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginRepository;
import com.workforcehub.plugin.sdk.InvocationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 插件权限校验服务。
 * 三重校验：插件能力授权 + 动作授权 + 调用者权限。
 * 能力码以宿主数据库为准，不信任插件 Descriptor。
 */
@Component
public class PluginPermissionService {

    private final JdbcTemplate jdbc;
    private final PluginRepository pluginRepository;

    public PluginPermissionService(JdbcTemplate jdbc, PluginRepository pluginRepository) {
        this.jdbc = jdbc;
        this.pluginRepository = pluginRepository;
    }

    /**
     * 检查插件是否被授予某能力（数据库为准）。
     */
    public boolean hasPluginCapability(String pluginId, String capabilityCode) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_capability WHERE plugin_id = ? AND capability_code = ?",
            Integer.class, pluginId, capabilityCode
        );
        return count != null && count > 0;
    }

    /**
     * 检查动作是否已授权（数据库为准）。
     */
    public boolean isActionAuthorized(String pluginId, String actionCode) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_action_permission WHERE plugin_id = ? AND action_code = ?",
            Integer.class, pluginId, actionCode
        );
        return count != null && count > 0;
    }

    /**
     * 检查调用者是否有权操作。
     */
    public boolean hasCallerPermission(InvocationContext ctx) {
        // 插件身份（定时任务等）允许
        if (ctx.getCallerType() == InvocationContext.CallerType.PLUGIN) {
            return true;
        }

        // 管理员：检查是否有 ADMIN_PLUGIN_CALL 权限
        if (ctx.getCallerType() == InvocationContext.CallerType.ADMIN) {
            return hasAdminPermission(ctx.getUserId(), "ADMIN_PLUGIN_CALL");
        }

        // 普通员工：默认允许调用已授权的插件动作
        // 安全边界在动作授权表，不在员工身份
        if (ctx.getCallerType() == InvocationContext.CallerType.USER) {
            return true;
        }

        return false;
    }

    /**
     * 完整权限校验（三重）。
     *
     * @throws PermissionDeniedException 权限不足时抛出
     */
    public void checkPermission(InvocationContext ctx, String pluginId, String actionCode, String capabilityCode) {
        // 1. 检查插件是否被授予该能力（数据库为准）
        if (!hasPluginCapability(pluginId, capabilityCode)) {
            throw new PermissionDeniedException(
                "插件未被授予能力: " + capabilityCode);
        }

        // 2. 检查动作是否已授权（数据库为准）
        if (!isActionAuthorized(pluginId, actionCode)) {
            throw new PermissionDeniedException(
                "动作未授权: " + actionCode);
        }

        // 3. 检查调用者权限
        if (!hasCallerPermission(ctx)) {
            throw new PermissionDeniedException(
                "调用者无权执行此操作");
        }
    }

    private boolean hasAdminPermission(Long userId, String permissionCode) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_user_role ur " +
            "JOIN admin_role_permission rp ON ur.role_id = rp.role_id " +
            "JOIN admin_permission p ON rp.permission_id = p.id " +
            "WHERE ur.admin_user_id = ? AND p.perm_code = ?",
            Integer.class, userId, permissionCode
        );
        return count != null && count > 0;
    }

    public static class PermissionDeniedException extends RuntimeException {
        public PermissionDeniedException(String message) {
            super(message);
        }
    }
}
