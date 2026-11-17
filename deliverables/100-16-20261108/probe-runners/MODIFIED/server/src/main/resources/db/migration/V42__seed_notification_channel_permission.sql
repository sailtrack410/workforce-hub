INSERT INTO admin_permission (
    id, perm_code, perm_name, module_code, action_code, status, created_by, updated_by, is_deleted
) VALUES
    (7010, 'ADMIN_NOTIFICATION_CHANNEL_MANAGE', '通知渠道配置', 'NOTIFICATION', 'MANAGE', 'ACTIVE', NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
    perm_name = VALUES(perm_name),
    module_code = VALUES(module_code),
    action_code = VALUES(action_code),
    status = VALUES(status),
    updated_by = VALUES(updated_by),
    is_deleted = VALUES(is_deleted);

INSERT INTO admin_role_permission (
    id, role_id, permission_id, created_by
) VALUES
    (6110, 6001, 7010, NULL)
ON DUPLICATE KEY UPDATE
    created_by = VALUES(created_by);
