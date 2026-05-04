-- ============================================================
-- Workforce Hub - 文件上传策略 ownerType 注释口径校准
-- 版本: V6
-- 说明:
--   - 统一 DDL 注释中的群文件 ownerType 示例
--   - 与 API 契约保持 IM_GROUP_FILE 口径一致
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE `sys_file_upload_policy`
    MODIFY COLUMN `owner_type` VARCHAR(64) NOT NULL
        COMMENT '文件归属场景，如 PERSONAL_FILE/WF_REQUEST/IM_GROUP_FILE';
