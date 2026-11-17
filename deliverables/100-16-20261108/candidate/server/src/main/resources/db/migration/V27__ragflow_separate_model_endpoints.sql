ALTER TABLE `kb_ragflow_setting`
    ADD COLUMN `ocr_base_url` VARCHAR(512) DEFAULT NULL COMMENT 'OCR服务URL' AFTER `api_key`,
    ADD COLUMN `ocr_api_key` VARCHAR(1024) DEFAULT NULL COMMENT 'OCR API Key' AFTER `ocr_base_url`,
    ADD COLUMN `embedding_base_url` VARCHAR(512) DEFAULT NULL COMMENT '嵌入服务URL' AFTER `ocr_api_key`,
    ADD COLUMN `embedding_api_key` VARCHAR(1024) DEFAULT NULL COMMENT '嵌入 API Key' AFTER `embedding_base_url`,
    ADD COLUMN `rerank_base_url` VARCHAR(512) DEFAULT NULL COMMENT '重排序服务URL' AFTER `embedding_api_key`,
    ADD COLUMN `rerank_api_key` VARCHAR(1024) DEFAULT NULL COMMENT '重排序 API Key' AFTER `rerank_base_url`;

UPDATE `kb_ragflow_setting`
SET `ocr_base_url` = `base_url`,
    `ocr_api_key` = `api_key`,
    `embedding_base_url` = `base_url`,
    `embedding_api_key` = `api_key`,
    `rerank_base_url` = `base_url`,
    `rerank_api_key` = `api_key`
WHERE `id` = 1;
