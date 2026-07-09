CREATE TABLE `wf_req_generic` (
    `id`                BIGINT       NOT NULL COMMENT '主键',
    `request_id`        BIGINT       NOT NULL COMMENT '申请头ID',
    `process_code`      VARCHAR(64)  NOT NULL COMMENT '流程编码',
    `summary`           VARCHAR(512) DEFAULT NULL COMMENT '申请摘要',
    `form_payload_json` JSON         NOT NULL COMMENT '通用表单数据',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_id` (`request_id`),
    KEY `idx_process_code` (`process_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用OA申请明细';
