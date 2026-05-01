-- ============================================================
-- Workforce Hub - module-org 第一批查询接口本地冒烟种子数据
-- 用途：
--   - 本地开发 / 联调 / 冒烟测试
--   - 为 docs/API测试用例_module-org第一批查询接口.md 提供可重复执行的数据基线
-- 注意：
--   - 仅限本地或临时测试环境，不要直接在共享环境执行
--   - 账号密码使用 BCrypt 哈希，明文见 API 测试文档
-- ============================================================

START TRANSACTION;

INSERT INTO org_department (
    id, parent_id, tree_path, dept_code, dept_name, dept_type, dept_level, leader_user_id,
    status, created_by, updated_by, is_deleted
) VALUES
    (100, NULL, '/100/', 'HQ', '总部', 'COMPANY', 0, NULL, 'ACTIVE', NULL, NULL, 0),
    (110, 100, '/100/110/', 'RD', '研发中心', 'DEPARTMENT', 1, 2002, 'ACTIVE', NULL, NULL, 0),
    (111, 110, '/100/110/111/', 'RD-BE', '后端组', 'TEAM', 2, 2002, 'ACTIVE', NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    tree_path = VALUES(tree_path),
    dept_name = VALUES(dept_name),
    dept_type = VALUES(dept_type),
    dept_level = VALUES(dept_level),
    leader_user_id = VALUES(leader_user_id),
    status = VALUES(status),
    updated_by = VALUES(updated_by),
    is_deleted = VALUES(is_deleted);

INSERT INTO org_position (
    id, position_code, position_name, position_category, status, created_by, updated_by, is_deleted
) VALUES
    (3001, 'DEV_BACKEND', '后端开发工程师', 'ENGINEER', 'ACTIVE', NULL, NULL, 0),
    (3002, 'DEV_MANAGER', '研发经理', 'MANAGER', 'ACTIVE', NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
    position_name = VALUES(position_name),
    position_category = VALUES(position_category),
    status = VALUES(status),
    updated_by = VALUES(updated_by),
    is_deleted = VALUES(is_deleted);

INSERT INTO org_user (
    id, employee_no, real_name, gender, mobile, primary_dept_id, position_id, manager_user_id,
    employment_status, hire_date, regularized_at, service_start_date, annual_leave_start_date,
    work_location, status, created_by, updated_by, is_deleted
) VALUES
    (2002, 'E1002', '李四', 'MALE', '13800000002', 110, 3002, NULL,
     'ACTIVE', '2023-03-01', '2023-06-01', '2023-03-01', '2023-03-01',
     '杭州', 'ACTIVE', NULL, NULL, 0),
    (2003, 'E1003', '张三', 'MALE', '13800000003', 111, 3001, 2002,
     'ACTIVE', '2024-01-15', '2024-04-15', '2024-01-15', '2024-01-15',
     '杭州', 'ACTIVE', NULL, NULL, 0),
    (2004, 'E1004', '王五', 'MALE', '13800000004', 111, 3001, 2002,
     'PROBATION', '2025-12-01', NULL, '2025-12-01', '2025-12-01',
     '杭州', 'ACTIVE', NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
    employee_no = VALUES(employee_no),
    real_name = VALUES(real_name),
    gender = VALUES(gender),
    mobile = VALUES(mobile),
    primary_dept_id = VALUES(primary_dept_id),
    position_id = VALUES(position_id),
    manager_user_id = VALUES(manager_user_id),
    employment_status = VALUES(employment_status),
    hire_date = VALUES(hire_date),
    regularized_at = VALUES(regularized_at),
    service_start_date = VALUES(service_start_date),
    annual_leave_start_date = VALUES(annual_leave_start_date),
    work_location = VALUES(work_location),
    status = VALUES(status),
    updated_by = VALUES(updated_by),
    is_deleted = VALUES(is_deleted);

INSERT INTO org_department_leader (
    id, dept_id, user_id, leader_type, is_primary, sort_order, status, created_by, updated_by
) VALUES
    (4101, 110, 2002, 'PRIMARY', 1, 1, 'ACTIVE', NULL, NULL),
    (4102, 111, 2002, 'PRIMARY', 1, 1, 'ACTIVE', NULL, NULL)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    leader_type = VALUES(leader_type),
    is_primary = VALUES(is_primary),
    sort_order = VALUES(sort_order),
    status = VALUES(status),
    updated_by = VALUES(updated_by);

INSERT INTO auth_user_account (
    id, user_id, login_name, password_hash, password_salt, email, email_verified_at,
    account_status, password_updated_at, created_by, updated_by, is_deleted
) VALUES
    (9002, 2002, 'lisi.manager', '$2a$10$CDmj/WrSdetJAbxwGsZnLOdVsia5y4KIrPkGz8NoEnDvCUX8Spx.G', NULL, 'lisi@example.com', NOW(),
     'ACTIVE', NOW(), NULL, NULL, 0),
    (9003, 2003, 'zhangsan.employee', '$2a$10$CDmj/WrSdetJAbxwGsZnLOdVsia5y4KIrPkGz8NoEnDvCUX8Spx.G', NULL, 'zhangsan@example.com', NOW(),
     'ACTIVE', NOW(), NULL, NULL, 0),
    (9004, 2004, 'wangwu.employee', '$2a$10$CDmj/WrSdetJAbxwGsZnLOdVsia5y4KIrPkGz8NoEnDvCUX8Spx.G', NULL, 'wangwu@example.com', NOW(),
     'ACTIVE', NOW(), NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
    login_name = VALUES(login_name),
    password_hash = VALUES(password_hash),
    password_salt = VALUES(password_salt),
    email = VALUES(email),
    email_verified_at = VALUES(email_verified_at),
    account_status = VALUES(account_status),
    password_updated_at = VALUES(password_updated_at),
    updated_by = VALUES(updated_by),
    is_deleted = VALUES(is_deleted);

INSERT INTO admin_user (
    id, username, password_hash, password_salt, display_name, email, status,
    created_by, updated_by, is_deleted
) VALUES
    (8001, 'admin.demo', '$2a$10$hAQ4ZLvpY3.VTOhNEdrmS.GNWqfGc3aijjjS5.Y74WME6RRvOU/fu', NULL, '演示管理员', 'admin.demo@example.com',
     'ACTIVE', NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
    username = VALUES(username),
    password_hash = VALUES(password_hash),
    password_salt = VALUES(password_salt),
    display_name = VALUES(display_name),
    email = VALUES(email),
    status = VALUES(status),
    updated_by = VALUES(updated_by),
    is_deleted = VALUES(is_deleted);

COMMIT;
