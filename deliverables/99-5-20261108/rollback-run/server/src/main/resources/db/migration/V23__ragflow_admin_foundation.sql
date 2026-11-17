ALTER TABLE `kb_space`
    ADD COLUMN `description` VARCHAR(512) DEFAULT NULL COMMENT '空间描述' AFTER `space_code`,
    ADD COLUMN `document_count` INT NOT NULL DEFAULT 0 COMMENT '文档数' AFTER `visibility`,
    ADD COLUMN `chunk_count` INT NOT NULL DEFAULT 0 COMMENT '切片数' AFTER `document_count`;

ALTER TABLE `kb_space_acl`
    ADD COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态' AFTER `perm_code`,
    ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `created_at`,
    ADD COLUMN `updated_by` BIGINT DEFAULT NULL AFTER `created_by`,
    ADD UNIQUE KEY `uk_space_subject_perm` (`space_id`, `subject_type`, `subject_id`, `perm_code`);

ALTER TABLE `kb_document`
    ADD COLUMN `attachment_ref_id` BIGINT DEFAULT NULL COMMENT '附件引用ID' AFTER `source_file_object_id`,
    ADD COLUMN `mime_type` VARCHAR(128) DEFAULT NULL COMMENT 'MIME类型' AFTER `title`,
    ADD COLUMN `file_size` BIGINT DEFAULT NULL COMMENT '文件大小' AFTER `mime_type`,
    ADD COLUMN `parser_type` VARCHAR(32) NOT NULL DEFAULT 'AUTO' COMMENT '解析方式' AFTER `content_hash`,
    ADD KEY `idx_attachment_ref` (`attachment_ref_id`);

ALTER TABLE `kb_ingest_job`
    ADD COLUMN `space_id` BIGINT DEFAULT NULL COMMENT '知识空间ID' AFTER `kb_document_id`,
    ADD COLUMN `source_file_object_id` BIGINT DEFAULT NULL COMMENT '源文件对象ID' AFTER `space_id`,
    ADD COLUMN `vector_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '向量状态' AFTER `chunk_count`,
    ADD COLUMN `vector_collection` VARCHAR(128) DEFAULT NULL COMMENT '向量集合' AFTER `vector_status`,
    ADD KEY `idx_space` (`space_id`);

ALTER TABLE `kb_chunk_manifest`
    MODIFY COLUMN `vector_row_id` BIGINT DEFAULT NULL COMMENT 'PostgreSQL向量表行ID',
    ADD COLUMN `space_id` BIGINT DEFAULT NULL COMMENT '知识空间ID' AFTER `kb_document_id`,
    ADD COLUMN `content_text` MEDIUMTEXT COMMENT '切片文本' AFTER `chunk_hash`,
    ADD COLUMN `token_count` INT DEFAULT NULL COMMENT 'Token数' AFTER `content_text`,
    ADD COLUMN `page_no` INT DEFAULT NULL COMMENT '页码' AFTER `token_count`,
    ADD KEY `idx_space` (`space_id`);
