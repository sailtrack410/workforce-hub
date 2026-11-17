ALTER TABLE `kb_space`
    ADD COLUMN `embedding_provider` VARCHAR(64) DEFAULT NULL COMMENT '嵌入模型供应商' AFTER `chunk_count`,
    ADD COLUMN `embedding_model` VARCHAR(128) DEFAULT NULL COMMENT '嵌入模型' AFTER `embedding_provider`,
    ADD COLUMN `embedding_dimension` INT DEFAULT NULL COMMENT '向量维度' AFTER `embedding_model`,
    ADD COLUMN `rerank_enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用重排序' AFTER `embedding_dimension`,
    ADD COLUMN `rerank_provider` VARCHAR(64) DEFAULT NULL COMMENT '重排序供应商' AFTER `rerank_enabled`,
    ADD COLUMN `rerank_model` VARCHAR(128) DEFAULT NULL COMMENT '重排序模型' AFTER `rerank_provider`,
    ADD COLUMN `chunk_size` INT NOT NULL DEFAULT 800 COMMENT '切片大小' AFTER `rerank_model`,
    ADD COLUMN `chunk_overlap` INT NOT NULL DEFAULT 100 COMMENT '切片重叠' AFTER `chunk_size`,
    ADD COLUMN `retrieval_top_k` INT NOT NULL DEFAULT 5 COMMENT '召回数量' AFTER `chunk_overlap`,
    ADD COLUMN `score_threshold` DECIMAL(6,4) DEFAULT NULL COMMENT '分数阈值' AFTER `retrieval_top_k`;

INSERT INTO `sys_file_upload_policy`
    (`id`, `policy_code`, `policy_name`, `owner_type`, `max_file_size_bytes`,
     `allowed_extensions`, `allowed_mime_types`, `status`, `created_by`)
VALUES
    (
        5006,
        'RAGFLOW_DOCUMENT_DEFAULT_200M',
        'RAGFlow知识库文件默认上传策略',
        'RAGFLOW_DOCUMENT',
        209715200,
        '["txt","md","pdf","doc","docx","xls","xlsx","ppt","pptx","csv","png","jpg","jpeg","webp"]',
        '["text/plain","text/markdown","text/csv","application/pdf","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-powerpoint","application/vnd.openxmlformats-officedocument.presentationml.presentation","image/png","image/jpeg","image/webp"]',
        'ENABLED',
        NULL
    );
