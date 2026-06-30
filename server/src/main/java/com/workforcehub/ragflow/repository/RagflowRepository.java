package com.workforcehub.ragflow.repository;

import com.workforcehub.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class RagflowRepository {

    private final JdbcTemplate jdbcTemplate;

    public RagflowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsSpaceCode(String spaceCode) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM kb_space
            WHERE space_code = ?
              AND is_deleted = 0
            """, Long.class, spaceCode);
        return count != null && count > 0;
    }

    public long countSpaces(String keyword) {
        List<Object> params = new ArrayList<>();
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM kb_space
            """ + whereSql(keyword, params), Long.class, params.toArray());
        return count == null ? 0L : count;
    }

    public List<RagflowSpaceRow> pageSpaces(String keyword, long offset, long limit) {
        List<Object> params = new ArrayList<>();
        String whereSql = whereSql(keyword, params);
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query("""
            SELECT id, space_name, space_code, description, visibility, document_count, chunk_count,
                   embedding_provider, embedding_model, embedding_dimension, rerank_enabled,
                   rerank_provider, rerank_model, chunk_size, chunk_overlap, retrieval_top_k,
                   score_threshold, status, created_at, updated_at
            FROM kb_space
            """ + whereSql + """
            ORDER BY created_at DESC, id DESC
            LIMIT ? OFFSET ?
            """, this::mapSpace, params.toArray());
    }

    public RagflowSpaceRow getSpaceOrThrow(Long spaceId) {
        List<RagflowSpaceRow> rows = jdbcTemplate.query("""
            SELECT id, space_name, space_code, description, visibility, document_count, chunk_count,
                   embedding_provider, embedding_model, embedding_dimension, rerank_enabled,
                   rerank_provider, rerank_model, chunk_size, chunk_overlap, retrieval_top_k,
                   score_threshold, status, created_at, updated_at
            FROM kb_space
            WHERE id = ?
              AND is_deleted = 0
            """, this::mapSpace, spaceId);
        if (rows.isEmpty()) {
            throw new BizException(404, "知识库不存在");
        }
        return rows.get(0);
    }

    public RagflowGlobalSettingRow getGlobalSetting() {
        List<RagflowGlobalSettingRow> rows = jdbcTemplate.query("""
            SELECT id, ocr_base_url, ocr_api_key, ocr_model,
                   embedding_base_url, embedding_api_key, embedding_model,
                   embedding_dimension, rerank_enabled,
                   rerank_base_url, rerank_api_key, rerank_model,
                   chunk_size, chunk_overlap, retrieval_top_k, score_threshold,
                   created_at, updated_at
            FROM kb_ragflow_setting
            WHERE id = 1
            """, this::mapGlobalSetting);
        if (rows.isEmpty()) {
            throw new BizException(404, "RAGFlow设置不存在");
        }
        return rows.get(0);
    }

    public void updateGlobalSetting(String ocrBaseUrl,
                                    String ocrApiKey,
                                    String ocrModel,
                                    String embeddingBaseUrl,
                                    String embeddingApiKey,
                                    String embeddingModel,
                                    Integer embeddingDimension,
                                    Boolean rerankEnabled,
                                    String rerankBaseUrl,
                                    String rerankApiKey,
                                    String rerankModel,
                                    Integer chunkSize,
                                    Integer chunkOverlap,
                                    Integer retrievalTopK,
                                    BigDecimal scoreThreshold,
                                    Long operatorId) {
        jdbcTemplate.update("""
            UPDATE kb_ragflow_setting
            SET ocr_base_url = ?,
                ocr_api_key = COALESCE(?, ocr_api_key),
                ocr_model = ?,
                embedding_base_url = ?,
                embedding_api_key = COALESCE(?, embedding_api_key),
                embedding_model = ?,
                embedding_dimension = ?,
                rerank_enabled = ?,
                rerank_base_url = ?,
                rerank_api_key = COALESCE(?, rerank_api_key),
                rerank_model = ?,
                chunk_size = ?,
                chunk_overlap = ?,
                retrieval_top_k = ?,
                score_threshold = ?,
                updated_by = ?
            WHERE id = 1
            """, ocrBaseUrl, ocrApiKey, ocrModel, embeddingBaseUrl, embeddingApiKey,
            embeddingModel, embeddingDimension, Boolean.TRUE.equals(rerankEnabled) ? 1 : 0,
            rerankBaseUrl, rerankApiKey, rerankModel,
            chunkSize, chunkOverlap, retrievalTopK, scoreThreshold, operatorId);
    }

    public String getRawOcrApiKey() {
        return jdbcTemplate.query("""
            SELECT ocr_api_key
            FROM kb_ragflow_setting
            WHERE id = 1
            """, rs -> rs.next() ? rs.getString("ocr_api_key") : null);
    }

    public String getRawEmbeddingApiKey() {
        return jdbcTemplate.query("""
            SELECT embedding_api_key
            FROM kb_ragflow_setting
            WHERE id = 1
            """, rs -> rs.next() ? rs.getString("embedding_api_key") : null);
    }

    public void insertSpace(Long spaceId,
                            String spaceName,
                            String spaceCode,
                            String description,
                            String visibility,
                            String status,
                            String embeddingProvider,
                            String embeddingModel,
                            Integer embeddingDimension,
                            Boolean rerankEnabled,
                            String rerankProvider,
                            String rerankModel,
                            Integer chunkSize,
                            Integer chunkOverlap,
                            Integer retrievalTopK,
                            BigDecimal scoreThreshold,
                            Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO kb_space
                (id, space_name, space_code, description, visibility, status,
                 embedding_provider, embedding_model, embedding_dimension, rerank_enabled,
                 rerank_provider, rerank_model, chunk_size, chunk_overlap, retrieval_top_k,
                 score_threshold, created_by, updated_by)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, spaceId, spaceName, spaceCode, description, visibility, status,
            embeddingProvider, embeddingModel, embeddingDimension, Boolean.TRUE.equals(rerankEnabled) ? 1 : 0,
            rerankProvider, rerankModel, chunkSize, chunkOverlap, retrievalTopK, scoreThreshold,
            operatorId, operatorId);
    }

    public void updateSpace(Long spaceId,
                            String spaceName,
                            String description,
                            String visibility,
                            String status,
                            String embeddingProvider,
                            String embeddingModel,
                            Integer embeddingDimension,
                            Boolean rerankEnabled,
                            String rerankProvider,
                            String rerankModel,
                            Integer chunkSize,
                            Integer chunkOverlap,
                            Integer retrievalTopK,
                            BigDecimal scoreThreshold,
                            Long operatorId) {
        jdbcTemplate.update("""
            UPDATE kb_space
            SET space_name = ?,
                description = ?,
                visibility = ?,
                status = ?,
                embedding_provider = ?,
                embedding_model = ?,
                embedding_dimension = ?,
                rerank_enabled = ?,
                rerank_provider = ?,
                rerank_model = ?,
                chunk_size = ?,
                chunk_overlap = ?,
                retrieval_top_k = ?,
                score_threshold = ?,
                updated_by = ?
            WHERE id = ?
              AND is_deleted = 0
            """, spaceName, description, visibility, status,
            embeddingProvider, embeddingModel, embeddingDimension, Boolean.TRUE.equals(rerankEnabled) ? 1 : 0,
            rerankProvider, rerankModel, chunkSize, chunkOverlap, retrievalTopK, scoreThreshold,
            operatorId, spaceId);
    }

    public List<RagflowDocumentRow> listDocuments(Long spaceId) {
        return jdbcTemplate.query("""
            SELECT d.id, d.space_id, d.attachment_ref_id, d.source_file_object_id, d.title,
                   d.mime_type, d.file_size, d.content_hash, d.parser_type, d.ingest_status,
                   d.latest_ingest_job_id, j.status AS ingest_job_status, j.vector_status,
                   j.chunk_count, j.error_message, d.created_at, d.updated_at
            FROM kb_document d
            LEFT JOIN kb_ingest_job j ON j.id = d.latest_ingest_job_id
            WHERE d.space_id = ?
              AND d.is_deleted = 0
            ORDER BY d.created_at DESC, d.id DESC
            """, this::mapDocument, spaceId);
    }

    public RagflowDocumentRow getDocumentOrThrow(Long spaceId, Long documentId) {
        List<RagflowDocumentRow> rows = jdbcTemplate.query("""
            SELECT d.id, d.space_id, d.attachment_ref_id, d.source_file_object_id, d.title,
                   d.mime_type, d.file_size, d.content_hash, d.parser_type, d.ingest_status,
                   d.latest_ingest_job_id, j.status AS ingest_job_status, j.vector_status,
                   j.chunk_count, j.error_message, d.created_at, d.updated_at
            FROM kb_document d
            LEFT JOIN kb_ingest_job j ON j.id = d.latest_ingest_job_id
            WHERE d.space_id = ?
              AND d.id = ?
              AND d.is_deleted = 0
            """, this::mapDocument, spaceId, documentId);
        if (rows.isEmpty()) {
            throw new BizException(404, "文档不存在");
        }
        return rows.get(0);
    }

    public boolean existsDocumentByAttachment(Long spaceId, Long attachmentRefId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM kb_document
            WHERE space_id = ?
              AND attachment_ref_id = ?
              AND is_deleted = 0
            """, Long.class, spaceId, attachmentRefId);
        return count != null && count > 0;
    }

    public void insertDocument(Long documentId,
                               Long spaceId,
                               Long attachmentRefId,
                               Long fileObjectId,
                               String title,
                               String mimeType,
                               Long fileSize,
                               String contentHash,
                               String parserType,
                               Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO kb_document
                (id, space_id, source_type, source_file_object_id, attachment_ref_id, title,
                 mime_type, file_size, content_hash, parser_type, ingest_status, status, created_by, updated_by)
            VALUES
                (?, ?, 'FILE', ?, ?, ?, ?, ?, ?, ?, 'PENDING', 'ACTIVE', ?, ?)
            """, documentId, spaceId, fileObjectId, attachmentRefId, title, mimeType, fileSize,
            contentHash, parserType, operatorId, operatorId);
    }

    public void insertIngestJob(Long jobId, Long documentId, Long spaceId, Long fileObjectId, Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO kb_ingest_job
                (id, kb_document_id, space_id, source_file_object_id, job_type, status,
                 chunk_count, vector_status, created_by)
            VALUES
                (?, ?, ?, ?, 'FULL', 'PENDING', 0, 'PENDING', ?)
            """, jobId, documentId, spaceId, fileObjectId, operatorId);
    }

    public void bindLatestIngestJob(Long documentId, Long jobId, Long operatorId) {
        jdbcTemplate.update("""
            UPDATE kb_document
            SET latest_ingest_job_id = ?,
                ingest_status = 'PENDING',
                updated_by = ?
            WHERE id = ?
            """, jobId, operatorId, documentId);
    }

    public void markIngestRunning(Long documentId, Long jobId) {
        jdbcTemplate.update("""
            UPDATE kb_document
            SET ingest_status = 'RUNNING'
            WHERE id = ?
            """, documentId);
        jdbcTemplate.update("""
            UPDATE kb_ingest_job
            SET status = 'RUNNING',
                vector_status = 'PENDING',
                started_at = NOW(),
                error_message = NULL
            WHERE id = ?
            """, jobId);
    }

    public void replaceChunks(Long spaceId, Long documentId, Long jobId, List<String> chunks, List<Long> vectorIds) {
        if (chunks.size() != vectorIds.size()) {
            throw new BizException(500, "切片与向量数量不一致");
        }
        jdbcTemplate.update("DELETE FROM kb_chunk_manifest WHERE kb_document_id = ?", documentId);
        int chunkNo = 1;
        for (String chunk : chunks) {
            jdbcTemplate.update("""
                INSERT INTO kb_chunk_manifest
                    (id, kb_document_id, ingest_job_id, chunk_no, chunk_hash, space_id, content_text,
                 token_count, vector_row_id, status)
                VALUES
                    (?, ?, ?, ?, SHA2(?, 256), ?, ?, ?, ?, 'ACTIVE')
                """, com.workforcehub.common.util.IdGenerator.nextId(), documentId, jobId, chunkNo, chunk, spaceId, chunk, chunk.length(), vectorIds.get(chunkNo - 1));
            chunkNo++;
        }
    }

    public void markIngestSuccess(Long documentId, Long jobId, String contentText, int chunkCount) {
        jdbcTemplate.update("""
            UPDATE kb_document
            SET ingest_status = 'SUCCESS',
                content_text = ?
            WHERE id = ?
            """, contentText, documentId);
        jdbcTemplate.update("""
            UPDATE kb_ingest_job
            SET status = 'SUCCESS',
                vector_status = 'SUCCESS',
                chunk_count = ?,
                finished_at = NOW(),
                error_message = NULL
            WHERE id = ?
            """, chunkCount, jobId);
    }

    public void markIngestFailed(Long documentId, Long jobId, String message) {
        jdbcTemplate.update("""
            UPDATE kb_document
            SET ingest_status = 'FAILED'
            WHERE id = ?
            """, documentId);
        jdbcTemplate.update("""
            UPDATE kb_ingest_job
            SET status = 'FAILED',
                vector_status = 'FAILED',
                finished_at = NOW(),
                error_message = ?
            WHERE id = ?
            """, message, jobId);
    }

    public void softDeleteDocument(Long spaceId, Long documentId, Long operatorId) {
        jdbcTemplate.update("""
            UPDATE kb_document
            SET is_deleted = 1,
                updated_by = ?
            WHERE space_id = ?
              AND id = ?
              AND is_deleted = 0
            """, operatorId, spaceId, documentId);
    }

    public void deactivateDocumentChunks(Long documentId) {
        jdbcTemplate.update("""
            UPDATE kb_chunk_manifest
            SET status = 'INACTIVE'
            WHERE kb_document_id = ?
              AND status = 'ACTIVE'
            """, documentId);
    }

    public void refreshSpaceCounts(Long spaceId) {
        jdbcTemplate.update("""
            UPDATE kb_space
            SET document_count = (
                    SELECT COUNT(*)
                    FROM kb_document
                    WHERE space_id = ? AND is_deleted = 0
                ),
                chunk_count = (
                    SELECT COUNT(*)
                    FROM kb_chunk_manifest
                    WHERE space_id = ? AND status = 'ACTIVE'
                )
            WHERE id = ?
            """, spaceId, spaceId, spaceId);
    }

    public List<RagflowSearchHitRow> searchChunks(Long spaceId, String query, int limit) {
        String like = "%" + query.trim() + "%";
        return jdbcTemplate.query("""
            SELECT c.id AS chunk_id, d.id AS document_id, d.title AS document_title,
                   c.chunk_no, c.content_text, c.page_no
            FROM kb_chunk_manifest c
            JOIN kb_document d ON d.id = c.kb_document_id
            WHERE c.space_id = ?
              AND c.status = 'ACTIVE'
              AND d.is_deleted = 0
              AND c.content_text LIKE ?
            ORDER BY c.created_at DESC, c.id DESC
            LIMIT ?
            """, (rs, rowNum) -> new RagflowSearchHitRow(
                rs.getLong("chunk_id"),
                rs.getLong("document_id"),
                rs.getString("document_title"),
                rs.getInt("chunk_no"),
                rs.getString("content_text"),
                rs.getObject("page_no", Integer.class),
                null
            ), spaceId, like, limit);
    }

    public Map<Long, String> documentTitles(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(documentIds.size(), "?"));
        return jdbcTemplate.query("""
            SELECT id, title
            FROM kb_document
            WHERE id IN (
            """ + placeholders + """
            )
              AND is_deleted = 0
            """, (rs, rowNum) -> Map.entry(rs.getLong("id"), rs.getString("title")), documentIds.toArray()).stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left));
    }

    public List<RagflowAclRow> listAcl(Long spaceId) {
        return jdbcTemplate.query("""
            SELECT id, space_id, subject_type, subject_id, perm_code, status, created_at
            FROM kb_space_acl
            WHERE space_id = ?
              AND status = 'ACTIVE'
            ORDER BY created_at DESC, id DESC
            """, this::mapAcl, spaceId);
    }

    public void deleteAcl(Long spaceId) {
        jdbcTemplate.update("DELETE FROM kb_space_acl WHERE space_id = ?", spaceId);
    }

    public void insertAcl(Long aclId,
                          Long spaceId,
                          String subjectType,
                          Long subjectId,
                          String permCode,
                          Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO kb_space_acl
                (id, space_id, subject_type, subject_id, perm_code, status, created_by, updated_by)
            VALUES
                (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
            """, aclId, spaceId, subjectType, subjectId, permCode, operatorId, operatorId);
    }

    private String whereSql(String keyword, List<Object> params) {
        StringBuilder sql = new StringBuilder("WHERE is_deleted = 0");
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (space_name LIKE ? OR space_code LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
        }
        return sql + " ";
    }

    private RagflowSpaceRow mapSpace(ResultSet rs, int rowNum) throws SQLException {
        return new RagflowSpaceRow(
            rs.getLong("id"),
            rs.getString("space_name"),
            rs.getString("space_code"),
            rs.getString("description"),
            rs.getString("visibility"),
            rs.getInt("document_count"),
            rs.getInt("chunk_count"),
            rs.getString("embedding_provider"),
            rs.getString("embedding_model"),
            rs.getObject("embedding_dimension", Integer.class),
            rs.getInt("rerank_enabled") == 1,
            rs.getString("rerank_provider"),
            rs.getString("rerank_model"),
            rs.getObject("chunk_size", Integer.class),
            rs.getObject("chunk_overlap", Integer.class),
            rs.getObject("retrieval_top_k", Integer.class),
            rs.getBigDecimal("score_threshold"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private RagflowDocumentRow mapDocument(ResultSet rs, int rowNum) throws SQLException {
        return new RagflowDocumentRow(
            rs.getLong("id"),
            rs.getLong("space_id"),
            rs.getObject("attachment_ref_id", Long.class),
            rs.getObject("source_file_object_id", Long.class),
            rs.getString("title"),
            rs.getString("mime_type"),
            rs.getObject("file_size", Long.class),
            rs.getString("content_hash"),
            rs.getString("parser_type"),
            rs.getString("ingest_status"),
            rs.getObject("latest_ingest_job_id", Long.class),
            rs.getString("ingest_job_status"),
            rs.getString("vector_status"),
            rs.getObject("chunk_count", Integer.class),
            rs.getString("error_message"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private RagflowGlobalSettingRow mapGlobalSetting(ResultSet rs, int rowNum) throws SQLException {
        return new RagflowGlobalSettingRow(
            rs.getLong("id"),
            rs.getString("ocr_base_url"),
            rs.getString("ocr_api_key") != null && !rs.getString("ocr_api_key").isBlank(),
            rs.getString("ocr_model"),
            rs.getString("embedding_base_url"),
            rs.getString("embedding_api_key") != null && !rs.getString("embedding_api_key").isBlank(),
            rs.getString("embedding_model"),
            rs.getObject("embedding_dimension", Integer.class),
            rs.getInt("rerank_enabled") == 1,
            rs.getString("rerank_base_url"),
            rs.getString("rerank_api_key") != null && !rs.getString("rerank_api_key").isBlank(),
            rs.getString("rerank_model"),
            rs.getObject("chunk_size", Integer.class),
            rs.getObject("chunk_overlap", Integer.class),
            rs.getObject("retrieval_top_k", Integer.class),
            rs.getBigDecimal("score_threshold"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private RagflowAclRow mapAcl(ResultSet rs, int rowNum) throws SQLException {
        return new RagflowAclRow(
            rs.getLong("id"),
            rs.getLong("space_id"),
            rs.getString("subject_type"),
            rs.getLong("subject_id"),
            rs.getString("perm_code"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
}
