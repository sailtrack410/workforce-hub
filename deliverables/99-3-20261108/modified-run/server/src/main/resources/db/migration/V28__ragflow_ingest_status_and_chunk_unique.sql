ALTER TABLE `kb_document`
    ADD COLUMN `content_text` MEDIUMTEXT COMMENT '解析文本' AFTER `parser_type`;

ALTER TABLE `kb_chunk_manifest`
    ADD UNIQUE KEY `uk_document_chunk` (`kb_document_id`, `chunk_no`);
