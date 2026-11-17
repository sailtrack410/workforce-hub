-- ============================================================
-- Workforce Hub - module-workflow 审批申请附件关联
-- 版本: V9
-- 说明:
--   - 审批申请只保存附件引用 ID，不重复保存文件对象或二进制
--   - 第一版由 module-workflow 校验申请人对附件引用的使用权
-- ============================================================

SET NAMES utf8mb4;

CREATE TABLE `wf_request_attachment` (
    `id`                BIGINT   NOT NULL COMMENT '主键',
    `request_id`        BIGINT   NOT NULL COMMENT '审批申请ID',
    `attachment_ref_id` BIGINT   NOT NULL COMMENT '附件引用ID',
    `sort_order`        INT      NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_by`        BIGINT   DEFAULT NULL,
    `is_deleted`        TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_attachment_active` (`request_id`, `attachment_ref_id`, `is_deleted`),
    KEY `idx_attachment_ref` (`attachment_ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批申请附件关联';
