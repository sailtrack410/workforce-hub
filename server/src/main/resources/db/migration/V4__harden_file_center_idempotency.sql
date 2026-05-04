-- ============================================================
-- Workforce Hub - 文件中心幂等与空作用域唯一性加固
-- 版本: V4
-- 说明:
--   - MySQL 唯一索引允许多条 NULL，补 scope_key 生成列统一表达空作用域
--   - 同一上传票据只允许生成一条未删除附件引用
--   - 与服务层 upsert / 冲突回查配合，收口并发幂等风险
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE `sys_storage_quota_policy`
    ADD COLUMN `scope_key` VARCHAR(64)
        GENERATED ALWAYS AS (IFNULL(CAST(`scope_id` AS CHAR), '__NONE__')) STORED
        COMMENT '作用域唯一键，空作用域统一为__NONE__' AFTER `scope_id`,
    DROP INDEX `uk_quota_scope_active`,
    ADD UNIQUE KEY `uk_quota_scope_active` (`scope_type`, `scope_key`, `is_deleted`);

ALTER TABLE `sys_storage_usage`
    ADD COLUMN `scope_key` VARCHAR(64)
        GENERATED ALWAYS AS (IFNULL(CAST(`scope_id` AS CHAR), '__NONE__')) STORED
        COMMENT '作用域唯一键，空作用域统一为__NONE__' AFTER `scope_id`,
    DROP INDEX `uk_usage_scope`,
    ADD UNIQUE KEY `uk_usage_scope` (`scope_type`, `scope_key`);

ALTER TABLE `sys_attachment_ref`
    ADD UNIQUE KEY `uk_attachment_upload_ticket_active` (`source_upload_ticket_id`, `is_deleted`);
