CREATE TABLE `agt_llm_model` (
    `id`           BIGINT       NOT NULL COMMENT '主键',
    `provider`     VARCHAR(64)  NOT NULL COMMENT '供应商: openai/anthropic/deepseek/ollama',
    `model_name`   VARCHAR(128) NOT NULL COMMENT '模型名称',
    `display_name` VARCHAR(128) NOT NULL COMMENT '显示名称',
    `endpoint`     VARCHAR(512) DEFAULT NULL COMMENT 'API端点',
    `api_key`      VARCHAR(512) DEFAULT NULL COMMENT 'API密钥(加密存储)',
    `status`       VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`   BIGINT       DEFAULT NULL,
    `updated_by`   BIGINT       DEFAULT NULL,
    `is_deleted`   TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM模型配置';
