-- 修复 V42/V53 复用 7010 导致的权限覆盖：7010 保留通知渠道，插件调用使用未占用 ID。
UPDATE admin_permission
SET perm_code = 'ADMIN_NOTIFICATION_CHANNEL_MANAGE',
    perm_name = '通知渠道配置',
    module_code = 'NOTIFICATION',
    action_code = 'MANAGE',
    status = 'ACTIVE',
    updated_by = NULL,
    is_deleted = 0
WHERE id = 7010
  AND perm_code = 'ADMIN_PLUGIN_CALL';

UPDATE admin_permission
SET perm_name = '通知渠道配置',
    module_code = 'NOTIFICATION',
    action_code = 'MANAGE',
    status = 'ACTIVE',
    updated_by = NULL,
    is_deleted = 0
WHERE perm_code = 'ADMIN_NOTIFICATION_CHANNEL_MANAGE';

INSERT INTO admin_permission (
    id, perm_code, perm_name, module_code, action_code, status, created_by, updated_by, is_deleted
)
SELECT 7010, 'ADMIN_NOTIFICATION_CHANNEL_MANAGE', '通知渠道配置',
       'NOTIFICATION', 'MANAGE', 'ACTIVE', NULL, NULL, 0
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission WHERE id = 7010
)
  AND NOT EXISTS (
    SELECT 1 FROM admin_permission WHERE perm_code = 'ADMIN_NOTIFICATION_CHANNEL_MANAGE'
  );

-- 先修复已存在的插件调用权限；不存在时使用当前最大权限 ID 后的新 ID。
UPDATE admin_permission
SET perm_name = '插件调用权限',
    module_code = 'PLUGIN',
    action_code = 'CALL',
    status = 'ACTIVE',
    updated_by = NULL,
    is_deleted = 0
WHERE perm_code = 'ADMIN_PLUGIN_CALL';

INSERT INTO admin_permission (
    id, perm_code, perm_name, module_code, action_code, status, created_by, updated_by, is_deleted
)
SELECT seq.next_id, 'ADMIN_PLUGIN_CALL', '插件调用权限',
       'PLUGIN', 'CALL', 'ACTIVE', NULL, NULL, 0
FROM (
    SELECT COALESCE(MAX(id), 7000) + 1 AS next_id
    FROM admin_permission
) seq
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission WHERE perm_code = 'ADMIN_PLUGIN_CALL'
  );

-- 关系主键可能已被历史权限占用，使用当前最大值后的新 ID，禁止覆盖旧关系。
INSERT INTO admin_role_permission (id, role_id, permission_id, created_by)
SELECT seq.next_id, r.id, p.id, NULL
FROM admin_role r
JOIN admin_permission p ON p.perm_code = 'ADMIN_PLUGIN_CALL'
CROSS JOIN (
    SELECT COALESCE(MAX(id), 6100) + 1 AS next_id
    FROM admin_role_permission
) seq
WHERE r.role_code = 'SUPER_ADMIN'
  AND r.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM admin_role_permission existing
      WHERE existing.role_id = r.id
        AND existing.permission_id = p.id
  );
