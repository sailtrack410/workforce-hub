-- ============================================================
-- Workforce Hub - 文件中心清理任务运行记录
-- 版本: V8
-- 说明:
--   - 增加清理任务运行表和明细表
--   - 支撑数据库状态标记与对象存储删除的两阶段边界
--   - 为失败对象保留可排查、可重试的结构化记录
-- ============================================================

SET NAMES utf8mb4;

CREATE TABLE `sys_file_cleanup_job_run` (
    `id`                       BIGINT       NOT NULL COMMENT '主键',
    `job_run_id`               VARCHAR(64)  NOT NULL COMMENT '任务运行编号',
    `job_type`                 VARCHAR(64)  NOT NULL COMMENT '任务类型',
    `dry_run`                  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否只预演',
    `status`                   VARCHAR(32)  NOT NULL DEFAULT 'RUNNING' COMMENT '状态: RUNNING/SUCCESS/PARTIAL_FAILED/FAILED',
    `operator_id`              BIGINT       DEFAULT NULL COMMENT '执行管理员ID',
    `expired_upload_tickets`   BIGINT       NOT NULL DEFAULT 0 COMMENT '过期上传票据数',
    `expired_download_tickets` BIGINT       NOT NULL DEFAULT 0 COMMENT '过期下载票据数',
    `purged_attachment_refs`   BIGINT       NOT NULL DEFAULT 0 COMMENT '彻底清理附件引用数',
    `deleted_physical_objects` BIGINT       NOT NULL DEFAULT 0 COMMENT '物理对象删除成功数',
    `failed_physical_objects`  BIGINT       NOT NULL DEFAULT 0 COMMENT '物理对象删除失败数',
    `released_bytes`           BIGINT       NOT NULL DEFAULT 0 COMMENT '释放业务用量字节数',
    `error_message`            VARCHAR(512) DEFAULT NULL COMMENT '任务级失败原因',
    `started_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    `finished_at`              DATETIME     DEFAULT NULL COMMENT '结束时间',
    `created_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cleanup_job_run_id` (`job_run_id`),
    KEY `idx_cleanup_job_status` (`status`, `started_at`),
    KEY `idx_cleanup_job_type` (`job_type`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件清理任务运行记录';

CREATE TABLE `sys_file_cleanup_job_detail` (
    `id`               BIGINT       NOT NULL COMMENT '主键',
    `job_run_id`       VARCHAR(64)  NOT NULL COMMENT '任务运行编号',
    `item_type`        VARCHAR(64)  NOT NULL COMMENT '明细类型: EXPIRED_UPLOAD_TICKET/UNREFERENCED_FILE_OBJECT',
    `target_type`      VARCHAR(64)  NOT NULL COMMENT '目标类型: UPLOAD_TICKET/FILE_OBJECT',
    `target_id`        BIGINT       NOT NULL COMMENT '目标ID',
    `storage_provider` VARCHAR(32)  NOT NULL COMMENT '存储提供方',
    `bucket_name`      VARCHAR(128) DEFAULT NULL COMMENT '存储桶',
    `object_key`       VARCHAR(512) NOT NULL COMMENT '对象键',
    `status`           VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SUCCESS/FAILED',
    `attempt_count`    INT          NOT NULL DEFAULT 0 COMMENT '尝试次数',
    `error_message`    VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
    `processed_at`     DATETIME     DEFAULT NULL COMMENT '处理时间',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_cleanup_detail_run` (`job_run_id`, `status`),
    KEY `idx_cleanup_detail_target` (`target_type`, `target_id`),
    KEY `idx_cleanup_detail_object` (`storage_provider`, `bucket_name`, `object_key`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件清理任务明细';
