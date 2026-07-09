CREATE TABLE IF NOT EXISTS `notify_channel_config` (
    `id` BIGINT NOT NULL,
    `channel_code` VARCHAR(64) NOT NULL,
    `channel_type` VARCHAR(32) NOT NULL,
    `channel_name` VARCHAR(128) NOT NULL,
    `enabled` TINYINT NOT NULL DEFAULT 0,
    `host` VARCHAR(255) DEFAULT NULL,
    `port` INT DEFAULT NULL,
    `username` VARCHAR(255) DEFAULT NULL,
    `secret_value` VARCHAR(1024) DEFAULT NULL,
    `from_address` VARCHAR(255) DEFAULT NULL,
    `from_name` VARCHAR(128) DEFAULT NULL,
    `ssl_enabled` TINYINT NOT NULL DEFAULT 0,
    `starttls_enabled` TINYINT NOT NULL DEFAULT 0,
    `timeout_millis` INT NOT NULL DEFAULT 5000,
    `last_test_success` TINYINT DEFAULT NULL,
    `last_test_message` VARCHAR(512) DEFAULT NULL,
    `last_tested_at` DATETIME DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `is_deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_notify_channel_code` (`channel_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知渠道配置';

INSERT INTO notify_channel_config (
    id, channel_code, channel_type, channel_name, enabled, timeout_millis, created_by, updated_by, is_deleted
) VALUES
    (8701, 'EMAIL_DEFAULT', 'EMAIL', '邮件', 0, 5000, NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
    channel_type = VALUES(channel_type),
    channel_name = VALUES(channel_name),
    updated_by = VALUES(updated_by),
    is_deleted = VALUES(is_deleted);
