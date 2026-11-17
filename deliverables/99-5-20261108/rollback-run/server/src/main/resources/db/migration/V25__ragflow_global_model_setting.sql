CREATE TABLE `kb_ragflow_setting` (
    `id`                  BIGINT       NOT NULL COMMENT '主键',
    `ocr_provider`        VARCHAR(64)  DEFAULT NULL COMMENT 'OCR模型供应商',
    `ocr_model`           VARCHAR(128) DEFAULT NULL COMMENT 'OCR模型',
    `embedding_provider`  VARCHAR(64)  DEFAULT NULL COMMENT '嵌入模型供应商',
    `embedding_model`     VARCHAR(128) DEFAULT NULL COMMENT '嵌入模型',
    `embedding_dimension` INT          DEFAULT NULL COMMENT '向量维度',
    `rerank_enabled`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否启用重排序',
    `rerank_provider`     VARCHAR(64)  DEFAULT NULL COMMENT '重排序供应商',
    `rerank_model`        VARCHAR(128) DEFAULT NULL COMMENT '重排序模型',
    `chunk_size`          INT          NOT NULL DEFAULT 800 COMMENT '切片大小',
    `chunk_overlap`       INT          NOT NULL DEFAULT 100 COMMENT '切片重叠',
    `retrieval_top_k`     INT          NOT NULL DEFAULT 5 COMMENT '召回数量',
    `score_threshold`     DECIMAL(6,4) DEFAULT NULL COMMENT '分数阈值',
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`          BIGINT       DEFAULT NULL,
    `updated_by`          BIGINT       DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAGFlow全局模型设置';

INSERT INTO `kb_ragflow_setting`
    (`id`, `chunk_size`, `chunk_overlap`, `retrieval_top_k`, `rerank_enabled`)
VALUES
    (1, 800, 100, 5, 0);
