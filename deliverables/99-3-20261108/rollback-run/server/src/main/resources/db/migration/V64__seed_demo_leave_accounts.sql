-- ============================================================
-- Workforce Hub - 演示账号假期账户种子
-- 版本: V64
-- 说明:
--   - 给三个演示账号（演示领导 22001 / 演示员工一 22002 / 演示员工二 22003）
--     发放年假（DEDUCT）与调休（DEDUCT）账户，让“我的假期”页有可演示的余额数据
--   - 同时补 GRANT 流水，余额与流水一致
--   - 幂等：按 uk_user_leave_type / uk_source_txn_key 守卫，重复执行不产生重复数据
-- ============================================================

SET NAMES utf8mb4;

-- 年假账户：演示领导 5 天、员工一 3 天、员工二 2 天（1 天 = 480 分钟）
INSERT INTO lv_account (id, user_id, leave_type_id, available_minutes, frozen_minutes, version, status, created_by, updated_by)
SELECT 64001, 22001, 39001, 2400, 0, 0, 'ACTIVE', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM lv_account WHERE user_id = 22001 AND leave_type_id = 39001);

INSERT INTO lv_account (id, user_id, leave_type_id, available_minutes, frozen_minutes, version, status, created_by, updated_by)
SELECT 64002, 22002, 39001, 1440, 0, 0, 'ACTIVE', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM lv_account WHERE user_id = 22002 AND leave_type_id = 39001);

INSERT INTO lv_account (id, user_id, leave_type_id, available_minutes, frozen_minutes, version, status, created_by, updated_by)
SELECT 64003, 22003, 39001, 960, 0, 0, 'ACTIVE', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM lv_account WHERE user_id = 22003 AND leave_type_id = 39001);

-- 调休账户：领导 2 天、员工一 1 天、员工二 0.5 天
INSERT INTO lv_account (id, user_id, leave_type_id, available_minutes, frozen_minutes, version, status, created_by, updated_by)
SELECT 64004, 22001, 39008, 960, 0, 0, 'ACTIVE', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM lv_account WHERE user_id = 22001 AND leave_type_id = 39008);

INSERT INTO lv_account (id, user_id, leave_type_id, available_minutes, frozen_minutes, version, status, created_by, updated_by)
SELECT 64005, 22002, 39008, 480, 0, 0, 'ACTIVE', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM lv_account WHERE user_id = 22002 AND leave_type_id = 39008);

INSERT INTO lv_account (id, user_id, leave_type_id, available_minutes, frozen_minutes, version, status, created_by, updated_by)
SELECT 64006, 22003, 39008, 240, 0, 0, 'ACTIVE', NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM lv_account WHERE user_id = 22003 AND leave_type_id = 39008);

-- 对应入账流水
INSERT INTO lv_account_txn (id, account_id, txn_type, change_minutes, balance_after, source_type, source_txn_key, effective_at)
SELECT 64101, 64001, 'GRANT', 2400, 2400, 'SYSTEM', 'seed-v64-annual-leader', NOW()
WHERE NOT EXISTS (SELECT 1 FROM lv_account_txn WHERE source_txn_key = 'seed-v64-annual-leader');

INSERT INTO lv_account_txn (id, account_id, txn_type, change_minutes, balance_after, source_type, source_txn_key, effective_at)
SELECT 64102, 64002, 'GRANT', 1440, 1440, 'SYSTEM', 'seed-v64-annual-emp1', NOW()
WHERE NOT EXISTS (SELECT 1 FROM lv_account_txn WHERE source_txn_key = 'seed-v64-annual-emp1');

INSERT INTO lv_account_txn (id, account_id, txn_type, change_minutes, balance_after, source_type, source_txn_key, effective_at)
SELECT 64103, 64003, 'GRANT', 960, 960, 'SYSTEM', 'seed-v64-annual-emp2', NOW()
WHERE NOT EXISTS (SELECT 1 FROM lv_account_txn WHERE source_txn_key = 'seed-v64-annual-emp2');

INSERT INTO lv_account_txn (id, account_id, txn_type, change_minutes, balance_after, source_type, source_txn_key, effective_at)
SELECT 64104, 64004, 'GRANT', 960, 960, 'SYSTEM', 'seed-v64-timeoff-leader', NOW()
WHERE NOT EXISTS (SELECT 1 FROM lv_account_txn WHERE source_txn_key = 'seed-v64-timeoff-leader');

INSERT INTO lv_account_txn (id, account_id, txn_type, change_minutes, balance_after, source_type, source_txn_key, effective_at)
SELECT 64105, 64005, 'GRANT', 480, 480, 'SYSTEM', 'seed-v64-timeoff-emp1', NOW()
WHERE NOT EXISTS (SELECT 1 FROM lv_account_txn WHERE source_txn_key = 'seed-v64-timeoff-emp1');

INSERT INTO lv_account_txn (id, account_id, txn_type, change_minutes, balance_after, source_type, source_txn_key, effective_at)
SELECT 64106, 64006, 'GRANT', 240, 240, 'SYSTEM', 'seed-v64-timeoff-emp2', NOW()
WHERE NOT EXISTS (SELECT 1 FROM lv_account_txn WHERE source_txn_key = 'seed-v64-timeoff-emp2');
