UPDATE admin_role
SET role_name = '超级管理员',
    status = 'ACTIVE',
    is_deleted = 0
WHERE role_code = 'SUPER_ADMIN';

INSERT INTO admin_role (
    id, role_code, role_name, status, created_by, updated_by, is_deleted
)
SELECT seq.next_id, 'SUPER_ADMIN', '超级管理员', 'ACTIVE', NULL, NULL, 0
FROM (
    SELECT COALESCE(MAX(id), 6000) + 1 AS next_id
    FROM admin_role
) seq
WHERE NOT EXISTS (
    SELECT 1
    FROM admin_role
    WHERE role_code = 'SUPER_ADMIN'
);

INSERT INTO admin_role_permission (
    id, role_id, permission_id, created_by
)
SELECT seq.base_id + ROW_NUMBER() OVER (ORDER BY p.id), r.id, p.id, NULL
FROM admin_role r
JOIN admin_permission p ON p.status = 'ACTIVE' AND p.is_deleted = 0
CROSS JOIN (
    SELECT COALESCE(MAX(id), 6100) AS base_id
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

INSERT INTO admin_user_role (
    id, admin_user_id, role_id, created_by
)
SELECT seq.next_id, au.id, r.id, NULL
FROM admin_user au
JOIN admin_role r ON r.role_code = 'SUPER_ADMIN' AND r.is_deleted = 0
CROSS JOIN (
    SELECT COALESCE(MAX(id), 6200) + 1 AS next_id
    FROM admin_user_role
) seq
WHERE au.username = 'admin.demo'
  AND au.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM admin_user_role existing
      WHERE existing.admin_user_id = au.id
        AND existing.role_id = r.id
  );
