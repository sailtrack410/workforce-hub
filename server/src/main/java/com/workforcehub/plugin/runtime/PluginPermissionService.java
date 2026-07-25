package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginRepository;
import com.workforcehub.plugin.sdk.InvocationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 插件权限校验服务。
 * 双重校验：插件能力 + 调用者权限。
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
     * 检查插件是否被授予某能力。
     */
    public boolean hasPluginCapability(String pluginId, String capabilityCode) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_capability WHERE plugin_id = ? AND capability_code = ?",
            Integer.class, pluginId, capabilityCode
        );
        return count != null && count > 0;
    }

    /**
     * 检查动作是否已授权。
     */
    public boolean isActionAuthorized(String pluginId, String actionCode, String capabilityCode) {
        // 检查动作权限表
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_action_permission WHERE plugin_id = ? AND action_code = ? AND capability_code = ?",
            Integer.class, pluginId, actionCode, capabilityCode
        );
        if (count != null && count > 0) {
            return true;
        }

        // 如果没有动作权限记录，检查插件能力
        return hasPluginCapability(pluginId, capabilityCode);
    }

    /**
     * 检查调用者是否有权操作目标数据。
     */
    public boolean hasUserPermission(InvocationContext ctx, String capabilityCode) {
        // 插件身份跳过用户权限检查
        if (ctx.getCallerType() == InvocationContext.CallerType.PLUGIN) {
            return true;
        }

        // 管理员检查权限码
        if (ctx.getCallerType() == InvocationContext.CallerType.ADMIN) {
            return hasAdminPermission(ctx.getUserId(), capabilityCode);
        }

        // 普通用户：默认拒绝，除非有明确授权
        return false;
    }

    /**
     * 完整权限校验。
     *
     * @throws PermissionDeniedException 权限不足时抛出
     */
    public void checkPermission(InvocationContext ctx, String pluginId, String actionCode, String capabilityCode) {
        // 1. 检查插件是否被授予该能力
        if (!hasPluginCapability(pluginId, capabilityCode)) {
            throw new PermissionDeniedException(
                "插件 [%s] 未被授予能力: %s".formatted(pluginId, capabilityCode));
        }

        // 2. 检查动作是否已授权
        if (!isActionAuthorized(pluginId, actionCode, capabilityCode)) {
            throw new PermissionDeniedException(
                "动作 [%s] 未授权".formatted(actionCode));
        }

        // 3. 检查调用者权限
        if (!hasUserPermission(ctx, capabilityCode)) {
            throw new PermissionDeniedException(
                "调用者无权执行此操作");
        }
    }

    private boolean hasAdminPermission(Long userId, String capabilityCode) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_user_role ur " +
            "JOIN admin_role_permission rp ON ur.role_id = rp.role_id " +
            "JOIN admin_permission p ON rp.permission_id = p.id " +
            "WHERE ur.admin_user_id = ? AND p.perm_code = ?",
            Integer.class, userId, capabilityCode
        );
        return count != null && count > 0;
    }

    public static class PermissionDeniedException extends RuntimeException {
        public PermissionDeniedException(String message) {
            super(message);
        }
    }
}
