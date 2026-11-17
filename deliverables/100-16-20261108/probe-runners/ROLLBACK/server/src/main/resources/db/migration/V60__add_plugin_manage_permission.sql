UPDATE admin_permission
SET perm_name = '插件管理',
    module_code = 'PLUGIN',
    action_code = 'MANAGE',
    status = 'ACTIVE',
    updated_by = NULL,
    is_deleted = 0
WHERE perm_code = 'ADMIN_PLUGIN_MANAGE';

INSERT INTO admin_permission (
    id, perm_code, perm_name, module_code, action_code, status, created_by, updated_by, is_deleted
)
SELECT seq.next_id, 'ADMIN_PLUGIN_MANAGE', '插件管理',
       'PLUGIN', 'MANAGE', 'ACTIVE', NULL, NULL, 0
FROM (
    SELECT COALESCE(MAX(id), 7000) + 1 AS next_id
    FROM admin_permission
) seq
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission WHERE perm_code = 'ADMIN_PLUGIN_MANAGE'
);

INSERT INTO admin_role_permission (id, role_id, permission_id, created_by)
SELECT seq.next_id, r.id, p.id, NULL
FROM admin_role r
JOIN admin_permission p ON p.perm_code = 'ADMIN_PLUGIN_MANAGE'
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
