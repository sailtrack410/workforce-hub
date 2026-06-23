-- ============================================================
-- Workforce Hub - 员工云空间目录
-- 版本: V29
-- 说明:
--   - 为员工个人云空间增加持久化目录
--   - 个人文件附件引用增加所在目录，支撑移动和复制后的独立位置
-- ============================================================

SET NAMES utf8mb4;

CREATE TABLE `sys_file_folder` (
    `id`               BIGINT       NOT NULL COMMENT '主键',
    `user_id`          BIGINT       NOT NULL COMMENT '所属员工用户ID',
    `parent_folder_id` BIGINT       DEFAULT NULL COMMENT '父文件夹ID',
    `parent_key`       VARCHAR(64)
        GENERATED ALWAYS AS (IFNULL(CAST(`parent_folder_id` AS CHAR), '__ROOT__')) STORED
        COMMENT '父级唯一键，根目录统一为__ROOT__',
    `folder_name`      VARCHAR(128) NOT NULL COMMENT '文件夹名称',
    `sort_order`       INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`       BIGINT       DEFAULT NULL,
    `updated_by`       BIGINT       DEFAULT NULL,
    `is_deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_folder_sibling_active` (`user_id`, `parent_key`, `folder_name`, `is_deleted`),
    KEY `idx_file_folder_parent` (`user_id`, `parent_folder_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工云空间文件夹';

ALTER TABLE `sys_attachment_ref`
    ADD COLUMN `folder_id` BIGINT DEFAULT NULL COMMENT '员工云空间所在文件夹ID' AFTER `visibility_scope`,
    ADD KEY `idx_attachment_folder` (`folder_id`);
