-- ============================================================
-- Workforce Hub - PostgreSQL + pgvector 向量库初始化 DDL
-- 版本: V1
-- 说明: 知识切片与向量检索表，非业务真相源
-- 注意: 需要先执行 CREATE EXTENSION IF NOT EXISTS vector;
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE kb_vector_chunk (
    id              BIGINT       NOT NULL PRIMARY KEY,
    kb_document_id  BIGINT       NOT NULL,
    space_id        BIGINT       NOT NULL,
    ingest_job_id   BIGINT       NOT NULL,
    chunk_no        INT          NOT NULL,
    chunk_text      TEXT         NOT NULL,
    embedding       vector(1536) NOT NULL,
    metadata_json   JSONB        DEFAULT NULL,
    content_hash    VARCHAR(128) DEFAULT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 列注释（PostgreSQL 语法）
COMMENT ON TABLE kb_vector_chunk IS '知识切片与向量检索表(非业务真相源)';
COMMENT ON COLUMN kb_vector_chunk.kb_document_id IS '知识文档ID(与MySQL对应)';
COMMENT ON COLUMN kb_vector_chunk.space_id IS '知识空间ID(与MySQL对应)';
COMMENT ON COLUMN kb_vector_chunk.ingest_job_id IS '入库任务ID(与MySQL对应)';
COMMENT ON COLUMN kb_vector_chunk.chunk_no IS '切片序号';
COMMENT ON COLUMN kb_vector_chunk.chunk_text IS '切片文本内容';
COMMENT ON COLUMN kb_vector_chunk.embedding IS 'Embedding向量(维度取决于模型)';
COMMENT ON COLUMN kb_vector_chunk.metadata_json IS '元数据JSON';
COMMENT ON COLUMN kb_vector_chunk.content_hash IS '内容哈希';
COMMENT ON COLUMN kb_vector_chunk.is_active IS '是否激活(检索只查TRUE)';

-- 唯一约束：同一入库任务的切片序号唯一
CREATE UNIQUE INDEX uk_ingest_chunk ON kb_vector_chunk (ingest_job_id, chunk_no);

-- 按文档查询索引
CREATE INDEX idx_kb_document ON kb_vector_chunk (kb_document_id);

-- 按空间+激活状态查询索引（RAG检索核心路径）
CREATE INDEX idx_space_active ON kb_vector_chunk (space_id, is_active) WHERE is_active = TRUE;

-- HNSW 向量索引（用于余弦相似度检索）
CREATE INDEX idx_embedding_cosine ON kb_vector_chunk
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64)
    WHERE is_active = TRUE;

-- 更新时间自动触发器
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_kb_vector_chunk_updated_at
    BEFORE UPDATE ON kb_vector_chunk
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
