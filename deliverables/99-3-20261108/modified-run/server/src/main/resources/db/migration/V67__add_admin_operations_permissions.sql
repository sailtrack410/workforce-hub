-- 99-1：补齐后台运营权限，仅给超级管理员绑定；其他角色由管理员明确授权。
UPDATE admin_permission
SET perm_name = '告警管理',
    module_code = 'ALERT',
    action_code = 'MANAGE',
    status = 'ACTIVE',
    updated_by = NULL,
    is_deleted = 0
WHERE perm_code = 'ADMIN_ALERT_MANAGE';

INSERT INTO admin_permission (
    id, perm_code, perm_name, module_code, action_code, status, created_by, updated_by, is_deleted
)
SELECT seq.next_id, 'ADMIN_ALERT_MANAGE', '告警管理',
       'ALERT', 'MANAGE', 'ACTIVE', NULL, NULL, 0
FROM (
    SELECT COALESCE(MAX(id), 7000) + 1 AS next_id
    FROM admin_permission
) seq
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission WHERE perm_code = 'ADMIN_ALERT_MANAGE'
);

INSERT INTO admin_role_permission (id, role_id, permission_id, created_by)
SELECT seq.next_id, r.id, p.id, NULL
FROM admin_role r
JOIN admin_permission p ON p.perm_code = 'ADMIN_ALERT_MANAGE'
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

UPDATE admin_permission
SET perm_name = 'IM投递治理',
    module_code = 'IM_OUTBOX',
    action_code = 'MANAGE',
    status = 'ACTIVE',
    updated_by = NULL,
    is_deleted = 0
WHERE perm_code = 'ADMIN_IM_OUTBOX_MANAGE';

INSERT INTO admin_permission (
    id, perm_code, perm_name, module_code, action_code, status, created_by, updated_by, is_deleted
)
SELECT seq.next_id, 'ADMIN_IM_OUTBOX_MANAGE', 'IM投递治理',
       'IM_OUTBOX', 'MANAGE', 'ACTIVE', NULL, NULL, 0
FROM (
    SELECT COALESCE(MAX(id), 7000) + 1 AS next_id
    FROM admin_permission
) seq
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission WHERE perm_code = 'ADMIN_IM_OUTBOX_MANAGE'
);

INSERT INTO admin_role_permission (id, role_id, permission_id, created_by)
SELECT seq.next_id, r.id, p.id, NULL
FROM admin_role r
JOIN admin_permission p ON p.perm_code = 'ADMIN_IM_OUTBOX_MANAGE'
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

UPDATE admin_permission
SET perm_name = '审计日志查询',
    module_code = 'AUDIT_LOG',
    action_code = 'VIEW',
    status = 'ACTIVE',
    updated_by = NULL,
    is_deleted = 0
WHERE perm_code = 'ADMIN_AUDIT_LOG_VIEW';

INSERT INTO admin_permission (
    id, perm_code, perm_name, module_code, action_code, status, created_by, updated_by, is_deleted
)
SELECT seq.next_id, 'ADMIN_AUDIT_LOG_VIEW', '审计日志查询',
       'AUDIT_LOG', 'VIEW', 'ACTIVE', NULL, NULL, 0
FROM (
    SELECT COALESCE(MAX(id), 7000) + 1 AS next_id
    FROM admin_permission
) seq
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission WHERE perm_code = 'ADMIN_AUDIT_LOG_VIEW'
);

INSERT INTO admin_role_permission (id, role_id, permission_id, created_by)
SELECT seq.next_id, r.id, p.id, NULL
FROM admin_role r
JOIN admin_permission p ON p.perm_code = 'ADMIN_AUDIT_LOG_VIEW'
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
