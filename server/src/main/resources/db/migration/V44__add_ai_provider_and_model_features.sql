CREATE TABLE `agt_llm_provider` (
    `id`                BIGINT       NOT NULL COMMENT '主键',
    `provider_type`     VARCHAR(64)  NOT NULL COMMENT '供应商类型',
    `display_name`      VARCHAR(128) NOT NULL COMMENT '显示名称',
    `base_url`          VARCHAR(512) DEFAULT NULL COMMENT 'Base URL',
    `api_key`           VARCHAR(512) DEFAULT NULL COMMENT 'API Key',
    `enabled`           TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    `last_test_success` TINYINT      DEFAULT NULL COMMENT '最近测试结果',
    `last_test_message` VARCHAR(512) DEFAULT NULL COMMENT '最近测试信息',
    `last_tested_at`    DATETIME     DEFAULT NULL COMMENT '最近测试时间',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`        BIGINT       DEFAULT NULL,
    `updated_by`        BIGINT       DEFAULT NULL,
    `is_deleted`        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agt_llm_provider_type` (`provider_type`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI供应商配置';

ALTER TABLE `agt_llm_model`
    ADD COLUMN `stream_output_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '流式输出' AFTER `default_flag`,
    ADD COLUMN `vision_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '视觉' AFTER `stream_output_flag`,
    ADD COLUMN `tool_call_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '工具调用' AFTER `vision_flag`,
    ADD COLUMN `structured_output_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '结构化输出' AFTER `tool_call_flag`,
    ADD COLUMN `reasoning_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '推理' AFTER `structured_output_flag`;
