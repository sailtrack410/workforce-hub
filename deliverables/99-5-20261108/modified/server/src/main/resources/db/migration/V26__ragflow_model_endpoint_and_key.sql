ALTER TABLE `kb_ragflow_setting`
    ADD COLUMN `base_url` VARCHAR(512) DEFAULT NULL COMMENT '模型服务URL' AFTER `id`,
    ADD COLUMN `api_key` VARCHAR(1024) DEFAULT NULL COMMENT '模型API Key' AFTER `base_url`;
