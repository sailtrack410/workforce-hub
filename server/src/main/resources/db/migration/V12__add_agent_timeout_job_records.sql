-- ============================================================
-- Workforce Hub - Agent 人工确认超时扫描生产化记录
-- 版本: V12
-- 说明:
--   - 增加通用任务锁表，支撑单实例/多实例定时任务互斥
--   - 增加 Agent 超时扫描运行记录，保留批次、命中数、取消数和跳过原因
-- ============================================================

SET NAMES utf8mb4;

CREATE TABLE `sys_job_lock` (
    `lock_name`    VARCHAR(128) NOT NULL COMMENT '锁名称',
    `owner_id`     VARCHAR(128) NOT NULL COMMENT '当前持有者',
    `locked_at`    DATETIME     NOT NULL COMMENT '加锁时间',
    `locked_until` DATETIME     NOT NULL COMMENT '锁过期时间',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`lock_name`),
    KEY `idx_job_lock_until` (`locked_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台任务互斥锁';

CREATE TABLE `agt_task_timeout_job_run` (
    `id`              BIGINT       NOT NULL COMMENT '主键',
    `job_run_id`      VARCHAR(64)  NOT NULL COMMENT '扫描运行编号',
    `trigger_type`    VARCHAR(32)  NOT NULL COMMENT '触发方式: SERVICE/MANUAL/SCHEDULED',
    `status`          VARCHAR(32)  NOT NULL DEFAULT 'RUNNING' COMMENT '状态: RUNNING/SUCCESS/SKIPPED/FAILED',
    `batch_size`      INT          NOT NULL COMMENT '批次大小',
    `scanned_count`   BIGINT       NOT NULL DEFAULT 0 COMMENT '本批扫描到的过期等待任务数',
    `cancelled_count` BIGINT       NOT NULL DEFAULT 0 COMMENT '成功取消任务数',
    `skipped_count`   BIGINT       NOT NULL DEFAULT 0 COMMENT '因锁或其他原因跳过次数',
    `lock_owner`      VARCHAR(128) DEFAULT NULL COMMENT '本次运行锁持有者',
    `error_message`   VARCHAR(512) DEFAULT NULL COMMENT '失败或跳过原因',
    `started_at`      DATETIME     NOT NULL COMMENT '开始时间',
    `finished_at`     DATETIME     DEFAULT NULL COMMENT '结束时间',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_timeout_job_run_id` (`job_run_id`),
    KEY `idx_agent_timeout_job_status` (`status`, `started_at`),
    KEY `idx_agent_timeout_job_trigger` (`trigger_type`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent人工确认超时扫描运行记录';
