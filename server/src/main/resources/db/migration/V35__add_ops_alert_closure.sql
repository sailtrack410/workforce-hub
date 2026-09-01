-- 版本: V35
-- 说明: 运营告警闭环基础表与内置告警规则种子
-- 历史说明: 本文件曾长期游离于 git 之外且 DDL 与实际库结构漂移（handled_by/operator_id 两套形状并存），
-- 2026-09-01 收编进版本库。DDL 与 V40（IF NOT EXISTS 兜底）保持同构，任意环境下两者先到先得、终点结构一致；
-- 审计列 created_by/updated_by 由 V57 统一补充，保证全新库与存量库终点一致。

CREATE TABLE IF NOT EXISTS `ops_alert_channel` (
    `id` BIGINT NOT NULL,
    `channel_code` VARCHAR(64) NOT NULL,
    `channel_name` VARCHAR(128) NOT NULL,
    `channel_type` VARCHAR(32) NOT NULL,
    `target_ref` VARCHAR(255) DEFAULT NULL,
    `enabled` TINYINT NOT NULL DEFAULT 1,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ops_alert_channel_code` (`channel_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营告警通知渠道';

CREATE TABLE IF NOT EXISTS `ops_alert_rule` (
    `id` BIGINT NOT NULL,
    `alert_code` VARCHAR(64) NOT NULL,
    `alert_name` VARCHAR(128) NOT NULL,
    `severity` VARCHAR(32) NOT NULL,
    `source_module` VARCHAR(64) NOT NULL,
    `enabled` TINYINT NOT NULL DEFAULT 1,
    `notify_enabled` TINYINT NOT NULL DEFAULT 0,
    `channel_id` BIGINT DEFAULT NULL,
    `cooldown_minutes` INT NOT NULL DEFAULT 30,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ops_alert_rule_code` (`alert_code`),
    KEY `idx_ops_alert_rule_channel` (`channel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营告警规则';

CREATE TABLE IF NOT EXISTS `ops_alert_resolution` (
    `id` BIGINT NOT NULL,
    `alert_code` VARCHAR(64) NOT NULL,
    `action_type` VARCHAR(64) NOT NULL,
    `comment` VARCHAR(512) DEFAULT NULL,
    `operator_id` BIGINT DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ops_alert_resolution_alert` (`alert_code`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运营告警处置记录';

INSERT INTO `ops_alert_channel`
    (`id`, `channel_code`, `channel_name`, `channel_type`, `enabled`)
VALUES
    (8501, 'IN_APP_DEFAULT', '后台站内告警', 'IN_APP', 1)
ON DUPLICATE KEY UPDATE
    `channel_name` = VALUES(`channel_name`),
    `channel_type` = VALUES(`channel_type`),
    `enabled` = VALUES(`enabled`);

INSERT INTO `ops_alert_rule`
    (`id`, `alert_code`, `alert_name`, `severity`, `source_module`, `enabled`, `notify_enabled`, `channel_id`, `cooldown_minutes`)
VALUES
    (8601, 'AGENT_TASK_FAILED', 'Agent任务失败', 'WARN', 'agent', 1, 0, 8501, 30),
    (8602, 'AGENT_TIMEOUT_SCAN_FAILED', 'Agent超时扫描失败', 'WARN', 'agent', 1, 0, 8501, 30),
    (8603, 'IM_OUTBOX_DEAD', 'IM outbox死信', 'CRITICAL', 'im', 1, 0, 8501, 30),
    (8604, 'IM_OUTBOX_STALE_SENDING', 'IM outbox发送滞留', 'WARN', 'im', 1, 0, 8501, 30),
    (8605, 'RAGFLOW_INGEST_FAILED', 'RAGFlow入库失败', 'WARN', 'ragflow', 1, 0, 8501, 30)
ON DUPLICATE KEY UPDATE
    `alert_name` = VALUES(`alert_name`),
    `severity` = VALUES(`severity`),
    `source_module` = VALUES(`source_module`),
    `enabled` = VALUES(`enabled`),
    `notify_enabled` = VALUES(`notify_enabled`),
    `channel_id` = VALUES(`channel_id`),
    `cooldown_minutes` = VALUES(`cooldown_minutes`);
