package com.workforcehub.ragflow.repository;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.ragflow.config.RagflowVectorProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Repository
public class RagflowVectorRepository {

    private final RagflowVectorProperties properties;
    private JdbcTemplate jdbcTemplate;

    public RagflowVectorRepository(RagflowVectorProperties properties) {
        this.properties = properties;
    }

    public List<Long> replaceDocumentVectors(Long spaceId,
                                             Long documentId,
                                             Long jobId,
                                             List<String> chunks,
                                             List<List<Double>> embeddings) {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ensureSchema(jdbcTemplate);
        jdbcTemplate.update("UPDATE kb_vector_chunk SET is_active = FALSE WHERE kb_document_id = ? AND is_active = TRUE", documentId);
        List<Long> vectorIds = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            Long vectorId = IdWorker.getId();
            jdbcTemplate.update("""
                INSERT INTO kb_vector_chunk
                    (id, kb_document_id, space_id, ingest_job_id, chunk_no, chunk_text, embedding, content_hash, is_active)
                VALUES
                    (?, ?, ?, ?, ?, ?, ?::vector, ?, TRUE)
                """, vectorId, documentId, spaceId, jobId, index + 1, chunks.get(index),
                vectorLiteral(embeddings.get(index)), sha256(chunks.get(index)));
            vectorIds.add(vectorId);
        }
        return vectorIds;
    }

    public List<RagflowSearchHitRow> search(Long spaceId,
                                            List<Double> queryEmbedding,
                                            int limit,
                                            BigDecimal scoreThreshold) {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ensureSchema(jdbcTemplate);
        String sql = """
            SELECT id, kb_document_id, chunk_no, chunk_text,
                   1 - (embedding <=> ?::vector) AS score
            FROM kb_vector_chunk
            WHERE space_id = ?
              AND is_active = TRUE
            """;
        List<Object> params = new ArrayList<>();
        params.add(vectorLiteral(queryEmbedding));
        params.add(spaceId);
        if (scoreThreshold != null) {
            sql += " AND 1 - (embedding <=> ?::vector) >= ? ";
            params.add(vectorLiteral(queryEmbedding));
            params.add(scoreThreshold);
        }
        sql += " ORDER BY embedding <=> ?::vector LIMIT ?";
        params.add(vectorLiteral(queryEmbedding));
        params.add(limit);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new RagflowSearchHitRow(
            rs.getLong("id"),
            rs.getLong("kb_document_id"),
            null,
            rs.getInt("chunk_no"),
            rs.getString("chunk_text"),
            null,
            rs.getBigDecimal("score")
        ), params.toArray());
    }

    public void deactivateDocumentVectors(Long documentId) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getUrl())) {
            return;
        }
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ensureSchema(jdbcTemplate);
        jdbcTemplate.update("UPDATE kb_vector_chunk SET is_active = FALSE WHERE kb_document_id = ? AND is_active = TRUE", documentId);
    }

    public void requireAvailable() {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getUrl())) {
            throw new BizException(422, "向量库未配置");
        }
    }

    private JdbcTemplate jdbcTemplate() {
        requireAvailable();
        if (jdbcTemplate == null) {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.postgresql.Driver");
            dataSource.setUrl(properties.getUrl());
            dataSource.setUsername(properties.getUsername());
            dataSource.setPassword(properties.getPassword());
            jdbcTemplate = new JdbcTemplate(dataSource);
        }
        return jdbcTemplate;
    }

    private void ensureSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS kb_vector_chunk (
                id BIGINT NOT NULL PRIMARY KEY,
                kb_document_id BIGINT NOT NULL,
                space_id BIGINT NOT NULL,
                ingest_job_id BIGINT NOT NULL,
                chunk_no INT NOT NULL,
                chunk_text TEXT NOT NULL,
                embedding vector NOT NULL,
                metadata_json JSONB DEFAULT NULL,
                content_hash VARCHAR(128) DEFAULT NULL,
                is_active BOOLEAN NOT NULL DEFAULT TRUE,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_kb_vector_space_active ON kb_vector_chunk (space_id, is_active) WHERE is_active = TRUE");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_kb_vector_document ON kb_vector_chunk (kb_document_id)");
    }

    private String vectorLiteral(List<Double> vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < vector.size(); index++) {
            Double value = vector.get(index);
            if (value == null || value.isNaN() || value.isInfinite()) {
                throw new BizException(502, "Embedding向量值无效");
            }
            if (index > 0) {
                builder.append(',');
            }
            builder.append(value);
        }
        return builder.append(']').toString();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new BizException(500, "切片哈希计算失败");
        }
    }
}
