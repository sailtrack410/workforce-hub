-- 插件调用权限
INSERT INTO admin_permission (
    id, perm_code, perm_name, module_code, action_code, status, created_by, updated_by, is_deleted
) VALUES
    (7010, 'ADMIN_PLUGIN_CALL', '插件调用权限', 'PLUGIN', 'CALL', 'ACTIVE', NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
    perm_name = VALUES(perm_name),
    module_code = VALUES(module_code),
    action_code = VALUES(action_code),
    status = VALUES(status),
    updated_by = VALUES(updated_by),
    is_deleted = VALUES(is_deleted);

-- 超级管理员拥有插件调用权限
INSERT INTO admin_role_permission (id, role_id, permission_id, created_by)
SELECT 6110, 6001, 7010, NULL
FROM admin_role
WHERE role_code = 'SUPER_ADMIN' AND is_deleted = 0
ON DUPLICATE KEY UPDATE created_by = VALUES(created_by);
