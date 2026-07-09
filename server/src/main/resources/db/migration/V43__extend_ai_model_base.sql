ALTER TABLE `agt_llm_model`
    ADD COLUMN `model_type` VARCHAR(32) NOT NULL DEFAULT 'TEXT' COMMENT '模型类型: TEXT/EMBEDDING/RERANK/TTS/VISION/OCR' AFTER `provider`,
    ADD COLUMN `default_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '默认模型' AFTER `api_key`,
    ADD COLUMN `last_test_success` TINYINT DEFAULT NULL COMMENT '最近测试结果' AFTER `status`,
    ADD COLUMN `last_test_message` VARCHAR(512) DEFAULT NULL COMMENT '最近测试信息' AFTER `last_test_success`,
    ADD COLUMN `last_tested_at` DATETIME DEFAULT NULL COMMENT '最近测试时间' AFTER `last_test_message`,
    ADD KEY `idx_agt_llm_model_type_default` (`model_type`, `default_flag`, `is_deleted`);

ALTER TABLE `kb_ragflow_setting`
    ADD COLUMN `ocr_model_id` BIGINT DEFAULT NULL COMMENT 'OCR基座模型ID' AFTER `ocr_model`,
    ADD COLUMN `embedding_model_id` BIGINT DEFAULT NULL COMMENT '嵌入基座模型ID' AFTER `embedding_model`,
    ADD COLUMN `rerank_model_id` BIGINT DEFAULT NULL COMMENT '重排基座模型ID' AFTER `rerank_model`;

ALTER TABLE `kb_space`
    ADD COLUMN `embedding_model_id` BIGINT DEFAULT NULL COMMENT '嵌入基座模型ID' AFTER `embedding_model`,
    ADD COLUMN `rerank_model_id` BIGINT DEFAULT NULL COMMENT '重排基座模型ID' AFTER `rerank_model`;
