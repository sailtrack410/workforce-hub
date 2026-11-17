INSERT INTO lv_type (id, leave_code, leave_name, balance_mode, status, created_by, updated_by)
SELECT 39001, 'ANNUAL', '年假', 'DEDUCT', 'ACTIVE', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM lv_type WHERE leave_code = 'ANNUAL' AND is_deleted = 0);

INSERT INTO lv_type (id, leave_code, leave_name, balance_mode, status, created_by, updated_by)
SELECT 39002, 'SICK', '病假', 'NO_DEDUCT', 'ACTIVE', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM lv_type WHERE leave_code = 'SICK' AND is_deleted = 0);

INSERT INTO lv_type (id, leave_code, leave_name, balance_mode, status, created_by, updated_by)
SELECT 39003, 'PERSONAL', '事假', 'NO_DEDUCT', 'ACTIVE', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM lv_type WHERE leave_code = 'PERSONAL' AND is_deleted = 0);

INSERT INTO lv_type (id, leave_code, leave_name, balance_mode, status, created_by, updated_by)
SELECT 39004, 'MARRIAGE', '婚假', 'NO_DEDUCT', 'ACTIVE', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM lv_type WHERE leave_code = 'MARRIAGE' AND is_deleted = 0);

INSERT INTO lv_type (id, leave_code, leave_name, balance_mode, status, created_by, updated_by)
SELECT 39005, 'MATERNITY', '产假', 'NO_DEDUCT', 'ACTIVE', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM lv_type WHERE leave_code = 'MATERNITY' AND is_deleted = 0);

INSERT INTO lv_type (id, leave_code, leave_name, balance_mode, status, created_by, updated_by)
SELECT 39006, 'PATERNITY', '陪产假', 'NO_DEDUCT', 'ACTIVE', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM lv_type WHERE leave_code = 'PATERNITY' AND is_deleted = 0);

INSERT INTO lv_type (id, leave_code, leave_name, balance_mode, status, created_by, updated_by)
SELECT 39007, 'BEREAVEMENT', '丧假', 'NO_DEDUCT', 'ACTIVE', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM lv_type WHERE leave_code = 'BEREAVEMENT' AND is_deleted = 0);

INSERT INTO lv_type (id, leave_code, leave_name, balance_mode, status, created_by, updated_by)
SELECT 39008, 'TIME_OFF', '调休', 'DEDUCT', 'ACTIVE', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM lv_type WHERE leave_code = 'TIME_OFF' AND is_deleted = 0);
