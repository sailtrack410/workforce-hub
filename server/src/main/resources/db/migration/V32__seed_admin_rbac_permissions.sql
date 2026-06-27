INSERT INTO admin_permission (
    id, perm_code, perm_name, module_code, action_code, status, created_by, updated_by, is_deleted
) VALUES
    (7001, 'ADMIN_RBAC_MANAGE', '角色权限管理', 'RBAC', 'MANAGE', 'ACTIVE', NULL, NULL, 0),
    (7002, 'ADMIN_ORG_MANAGE', '组织人员管理', 'ORG', 'MANAGE', 'ACTIVE', NULL, NULL, 0),
    (7003, 'ADMIN_ATTENDANCE_MANAGE', '考勤管理', 'ATTENDANCE', 'MANAGE', 'ACTIVE', NULL, NULL, 0),
    (7004, 'ADMIN_LEAVE_MANAGE', '假期管理', 'LEAVE', 'MANAGE', 'ACTIVE', NULL, NULL, 0),
    (7005, 'ADMIN_WORKFLOW_MANAGE', '审批治理', 'WORKFLOW', 'MANAGE', 'ACTIVE', NULL, NULL, 0),
    (7006, 'ADMIN_AGENT_MANAGE', 'Agent配置管理', 'AGENT', 'MANAGE', 'ACTIVE', NULL, NULL, 0),
    (7007, 'ADMIN_AGENT_TASK_MANAGE', 'Agent任务管理', 'AGENT_TASK', 'MANAGE', 'ACTIVE', NULL, NULL, 0),
    (7008, 'ADMIN_FILE_MANAGE', '文件管理', 'FILE', 'MANAGE', 'ACTIVE', NULL, NULL, 0),
    (7009, 'ADMIN_RAGFLOW_MANAGE', 'RAGFlow管理', 'RAGFLOW', 'MANAGE', 'ACTIVE', NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
    perm_name = VALUES(perm_name),
    module_code = VALUES(module_code),
    action_code = VALUES(action_code),
    status = VALUES(status),
    updated_by = VALUES(updated_by),
    is_deleted = VALUES(is_deleted);

INSERT INTO admin_role (
    id, role_code, role_name, status, created_by, updated_by, is_deleted
) VALUES
    (6001, 'SUPER_ADMIN', '超级管理员', 'ACTIVE', NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    status = VALUES(status),
    updated_by = VALUES(updated_by),
    is_deleted = VALUES(is_deleted);

INSERT INTO admin_role_permission (
    id, role_id, permission_id, created_by
) VALUES
    (6101, 6001, 7001, NULL),
    (6102, 6001, 7002, NULL),
    (6103, 6001, 7003, NULL),
    (6104, 6001, 7004, NULL),
    (6105, 6001, 7005, NULL),
    (6106, 6001, 7006, NULL),
    (6107, 6001, 7007, NULL),
    (6108, 6001, 7008, NULL),
    (6109, 6001, 7009, NULL)
ON DUPLICATE KEY UPDATE
    created_by = VALUES(created_by);

INSERT INTO admin_user_role (
    id, admin_user_id, role_id, created_by
)
SELECT 6201, 8001, 6001, NULL
FROM admin_user
WHERE id = 8001
ON DUPLICATE KEY UPDATE
    role_id = VALUES(role_id),
    created_by = VALUES(created_by);
