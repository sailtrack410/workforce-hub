ALTER TABLE `kb_space`
    MODIFY COLUMN `rerank_enabled` TINYINT DEFAULT NULL COMMENT '是否启用重排序，空表示继承全局设置',
    MODIFY COLUMN `chunk_size` INT DEFAULT NULL COMMENT '切片大小，空表示继承全局设置',
    MODIFY COLUMN `chunk_overlap` INT DEFAULT NULL COMMENT '切片重叠，空表示继承全局设置',
    MODIFY COLUMN `retrieval_top_k` INT DEFAULT NULL COMMENT '召回数量，空表示继承全局设置';

UPDATE `kb_space`
SET `embedding_model_id` = NULL,
    `embedding_dimension` = NULL,
    `rerank_enabled` = NULL,
    `rerank_model_id` = NULL,
    `chunk_size` = NULL,
    `chunk_overlap` = NULL,
    `retrieval_top_k` = NULL,
    `score_threshold` = NULL
WHERE `document_count` = 0
  AND `chunk_count` = 0;
