-- ============================================================
-- Workforce Hub - 文件引用与上传票据幂等关联
-- 版本: V3
-- 说明:
--   - 记录附件引用来源上传票据，确保确认上传重复提交时可准确返回原引用
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE `sys_attachment_ref`
    ADD COLUMN `source_upload_ticket_id` BIGINT DEFAULT NULL COMMENT '来源上传票据ID' AFTER `file_object_id`,
    ADD KEY `idx_attachment_upload_ticket` (`source_upload_ticket_id`);
