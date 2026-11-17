-- ============================================================
-- Workforce Hub - module-file 第一版文件中心增强
-- 版本: V2
-- 说明:
--   - 增强 V1 中已有的 sys_file_object / sys_attachment_ref / sys_upload_ticket
--   - 新增配额、用量、下载票据表
--   - 支撑 LOCAL/MINIO、两段式上传、sha256+size 去重、回收站和下载票据
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE `sys_file_object`
    ADD COLUMN `content_md5` VARCHAR(64) DEFAULT NULL COMMENT '内容MD5' AFTER `file_hash`,
    ADD COLUMN `content_sha256` VARCHAR(128) DEFAULT NULL COMMENT '内容SHA-256' AFTER `content_md5`,
    ADD COLUMN `ref_count` INT NOT NULL DEFAULT 0 COMMENT '有效业务引用数' AFTER `content_sha256`,
    ADD COLUMN `scan_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '扫描状态: PENDING/PASSED/FAILED/SKIPPED' AFTER `upload_status`,
    ADD COLUMN `cleanup_after` DATETIME DEFAULT NULL COMMENT '无引用物理对象可清理时间' AFTER `created_by`,
    ADD COLUMN `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除' AFTER `cleanup_after`;

ALTER TABLE `sys_file_object`
    ADD UNIQUE KEY `uk_file_sha256_size_active` (`content_sha256`, `file_size`, `is_deleted`),
    ADD KEY `idx_file_cleanup_after` (`cleanup_after`),
    ADD KEY `idx_file_scan_status` (`scan_status`);

ALTER TABLE `sys_attachment_ref`
    MODIFY COLUMN `owner_id` BIGINT DEFAULT NULL COMMENT '归属业务ID',
    ADD COLUMN `original_file_name` VARCHAR(256) DEFAULT NULL COMMENT '原始文件名' AFTER `file_object_id`,
    ADD COLUMN `uploader_type` VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '上传人类型: USER/ADMIN/AGENT' AFTER `display_name`,
    ADD COLUMN `uploader_id` BIGINT DEFAULT NULL COMMENT '上传人ID' AFTER `uploader_type`,
    ADD COLUMN `visibility_scope` VARCHAR(64) DEFAULT NULL COMMENT '可见范围' AFTER `uploader_id`,
    ADD COLUMN `deleted_at` DATETIME DEFAULT NULL COMMENT '移入回收站时间' AFTER `sort_order`,
    ADD COLUMN `deleted_by` BIGINT DEFAULT NULL COMMENT '删除人ID' AFTER `deleted_at`,
    ADD COLUMN `purge_after` DATETIME DEFAULT NULL COMMENT '回收站彻底清理时间' AFTER `deleted_by`,
    ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `created_by`,
    ADD COLUMN `updated_by` BIGINT DEFAULT NULL AFTER `updated_at`,
    ADD COLUMN `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除' AFTER `updated_by`;

ALTER TABLE `sys_attachment_ref`
    ADD KEY `idx_attachment_deleted_at` (`deleted_at`),
    ADD KEY `idx_attachment_purge_after` (`purge_after`),
    ADD KEY `idx_attachment_uploader` (`uploader_type`, `uploader_id`);

ALTER TABLE `sys_upload_ticket`
    ADD COLUMN `storage_provider` VARCHAR(32) NOT NULL DEFAULT 'LOCAL' COMMENT '存储提供方' AFTER `biz_id`,
    ADD COLUMN `bucket_name` VARCHAR(128) DEFAULT NULL COMMENT '存储桶' AFTER `storage_provider`,
    ADD COLUMN `content_md5` VARCHAR(64) DEFAULT NULL COMMENT '内容MD5' AFTER `file_size`,
    ADD COLUMN `content_sha256` VARCHAR(128) DEFAULT NULL COMMENT '内容SHA-256' AFTER `content_md5`,
    ADD COLUMN `client_request_id` VARCHAR(128) DEFAULT NULL COMMENT '客户端幂等ID' AFTER `object_key`,
    ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `created_by`,
    ADD COLUMN `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除' AFTER `updated_at`;

ALTER TABLE `sys_upload_ticket`
    ADD UNIQUE KEY `uk_upload_client_request` (`uploader_type`, `uploader_id`, `client_request_id`),
    ADD KEY `idx_upload_hash_size` (`content_sha256`, `file_size`),
    ADD KEY `idx_upload_status` (`status`);

CREATE TABLE `sys_storage_quota_policy` (
    `id`                BIGINT       NOT NULL COMMENT '主键',
    `policy_code`       VARCHAR(128) NOT NULL COMMENT '策略编码',
    `policy_name`       VARCHAR(128) NOT NULL COMMENT '策略名称',
    `scope_type`        VARCHAR(32)  NOT NULL COMMENT '作用域: GLOBAL/USER_DEFAULT/USER/DEPARTMENT/ROLE/GROUP/KNOWLEDGE_SPACE/TEMP_CACHE',
    `scope_id`          BIGINT       DEFAULT NULL COMMENT '作用域ID',
    `quota_bytes`       BIGINT       NOT NULL COMMENT '长期文件池配额',
    `cache_quota_bytes` BIGINT       DEFAULT NULL COMMENT '临时缓存池配额',
    `warning_threshold` DECIMAL(5,4) NOT NULL DEFAULT 0.8000 COMMENT '预警阈值',
    `block_threshold`   DECIMAL(5,4) NOT NULL DEFAULT 1.0000 COMMENT '阻断阈值',
    `status`            VARCHAR(32)  NOT NULL DEFAULT 'ENABLED',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`        BIGINT       DEFAULT NULL,
    `updated_by`        BIGINT       DEFAULT NULL,
    `is_deleted`        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quota_policy_code` (`policy_code`),
    UNIQUE KEY `uk_quota_scope_active` (`scope_type`, `scope_id`, `is_deleted`),
    KEY `idx_quota_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件中心配额策略';

CREATE TABLE `sys_storage_usage` (
    `id`               BIGINT      NOT NULL COMMENT '主键',
    `scope_type`       VARCHAR(32) NOT NULL COMMENT '作用域',
    `scope_id`         BIGINT      DEFAULT NULL COMMENT '作用域ID',
    `used_bytes`       BIGINT      NOT NULL DEFAULT 0 COMMENT '长期文件池已用',
    `cache_used_bytes` BIGINT      NOT NULL DEFAULT 0 COMMENT '临时缓存已用',
    `file_count`       BIGINT      NOT NULL DEFAULT 0 COMMENT '有效文件引用数',
    `created_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_usage_scope` (`scope_type`, `scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件中心用量统计';

CREATE TABLE `sys_download_ticket` (
    `id`                BIGINT       NOT NULL COMMENT '主键',
    `ticket_no`         VARCHAR(64)  NOT NULL COMMENT '下载票据编号',
    `attachment_ref_id` BIGINT       NOT NULL COMMENT '附件引用ID',
    `requester_type`    VARCHAR(32)  NOT NULL COMMENT '申请人类型: USER/ADMIN/AGENT',
    `requester_id`      BIGINT       NOT NULL COMMENT '申请人ID',
    `download_type`     VARCHAR(32)  NOT NULL DEFAULT 'SINGLE' COMMENT '下载类型',
    `download_url`      VARCHAR(512) NOT NULL COMMENT '短期下载入口',
    `reason`            VARCHAR(256) DEFAULT NULL COMMENT '下载原因',
    `expire_at`         DATETIME     NOT NULL COMMENT '过期时间',
    `status`            VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/USED/EXPIRED/REVOKED',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_download_ticket_no` (`ticket_no`),
    KEY `idx_download_attachment` (`attachment_ref_id`),
    KEY `idx_download_requester` (`requester_type`, `requester_id`),
    KEY `idx_download_expire` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短期下载票据';
