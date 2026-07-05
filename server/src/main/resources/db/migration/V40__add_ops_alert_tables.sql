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
