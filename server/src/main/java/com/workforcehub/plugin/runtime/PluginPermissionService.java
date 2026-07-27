package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.InvocationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 插件权限校验服务。
 * 三重校验：插件能力授权 + 动作授权 + 调用者权限。
 * 能力码以宿主数据库为准，不信任插件 Descriptor。
 */
@Component
public class PluginPermissionService {

    private final JdbcTemplate jdbc;

    public PluginPermissionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 仅检查插件能力（数据库为准）。
     * 供 HostService 代理逐次调用校验使用。
     */
    public void checkPluginCapability(String pluginId, String capabilityCode) {
        if (capabilityCode == null || capabilityCode.isBlank()) {
            throw new PermissionDeniedException("能力码不能为空");
        }
        if (!hasPluginCapability(pluginId, capabilityCode)) {
            throw new PermissionDeniedException("插件未被授予能力: " + capabilityCode);
        }
    }

    /**
     * 检查插件是否被授予某能力（数据库为准）。
     */
    private boolean hasPluginCapability(String pluginId, String capabilityCode) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_capability WHERE plugin_id = ? AND capability_code = ?",
            Integer.class, pluginId, capabilityCode
        );
        return count != null && count > 0;
    }

    /**
     * 获取动作授权对应的能力码（数据库为准）。
     * 必须存在明确映射，禁止回退为仅检查 sys_plugin_capability。
     */
    private String getActionCapability(String pluginId, String actionCode) {
        List<String> capabilityCodes = jdbc.query(
            "SELECT capability_code FROM sys_plugin_action_permission WHERE plugin_id = ? AND action_code = ?",
            (rs, rowNum) -> rs.getString("capability_code"),
            pluginId,
            actionCode
        );
        return capabilityCodes.isEmpty() ? null : capabilityCodes.get(0);
    }

    /**
     * 检查调用者是否有权操作。
     * CallerType.PLUGIN 只用于宿主内部调用（定时任务等）。
     * 普通员工暂未实现显式授权时，安全默认拒绝。
     */
    private boolean hasCallerPermission(InvocationContext ctx) {
        if (ctx.getCallerType() == InvocationContext.CallerType.PLUGIN) {
            return true;
        }
        if (ctx.getCallerType() == InvocationContext.CallerType.ADMIN) {
            return ctx.getUserId() != null && hasAdminPermission(ctx.getUserId(), "ADMIN_PLUGIN_CALL");
        }
        // 普通员工：默认拒绝
        return false;
    }

    /**
     * 完整权限校验（三重）。
     * 宿主能力配置为空不得跳过授权。
     */
    public void checkPermission(InvocationContext ctx, String pluginId, String actionCode) {
        // 1. 动作必须在授权表中有记录，能力码以宿主配置为准
        String capabilityCode = getActionCapability(pluginId, actionCode);
        if (capabilityCode == null || capabilityCode.isBlank()) {
            throw new PermissionDeniedException("动作未授权");
        }

        // 2. 检查插件是否被授予该能力
        if (!hasPluginCapability(pluginId, capabilityCode)) {
            throw new PermissionDeniedException("插件未被授予能力");
        }

        // 3. 检查调用者权限
        if (!hasCallerPermission(ctx)) {
            throw new PermissionDeniedException("调用者无权执行此操作");
        }
    }

    private boolean hasAdminPermission(Long userId, String permissionCode) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_user_role ur " +
            "JOIN admin_user u ON ur.admin_user_id = u.id " +
            "JOIN admin_role r ON ur.role_id = r.id " +
            "JOIN admin_role_permission rp ON ur.role_id = rp.role_id " +
            "JOIN admin_permission p ON rp.permission_id = p.id " +
            "WHERE ur.admin_user_id = ? AND p.perm_code = ? " +
            "AND u.status = 'ACTIVE' AND u.is_deleted = 0 " +
            "AND r.status = 'ACTIVE' AND r.is_deleted = 0 " +
            "AND p.status = 'ACTIVE' AND p.is_deleted = 0",
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
