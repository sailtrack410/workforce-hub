INSERT INTO admin_user_role (
    id, admin_user_id, role_id, created_by
)
SELECT seq.next_id, au.id, ar.id, NULL
FROM admin_user au
JOIN admin_role ar ON ar.role_code = 'SUPER_ADMIN' AND ar.is_deleted = 0
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
        AND existing.role_id = ar.id
  );
