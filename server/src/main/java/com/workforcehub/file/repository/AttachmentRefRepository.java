package com.workforcehub.file.repository;

import com.workforcehub.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AttachmentRefRepository {

    private final JdbcTemplate jdbcTemplate;

    public AttachmentRefRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long findIdByUploadTicket(Long uploadTicketId) {
        return jdbcTemplate.query("""
            SELECT id
            FROM sys_attachment_ref
            WHERE source_upload_ticket_id = ? AND is_deleted = 0
            ORDER BY created_at DESC
            LIMIT 1
            """, rs -> rs.next() ? rs.getLong("id") : null, uploadTicketId);
    }

    public void create(Long attachmentRefId,
                       String ownerType,
                       Long ownerId,
                       Long fileObjectId,
                       Long sourceUploadTicketId,
                       String originalFileName,
                       String displayName,
                       String uploaderType,
                       Long uploaderId,
                       Long folderId) {
        jdbcTemplate.update("""
            INSERT INTO sys_attachment_ref
                (id, owner_type, owner_id, file_object_id, source_upload_ticket_id, original_file_name,
                 display_name, uploader_type, uploader_id, visibility_scope, folder_id, created_by)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            attachmentRefId,
            ownerType,
            ownerId,
            fileObjectId,
            sourceUploadTicketId,
            originalFileName,
            displayName,
            uploaderType,
            uploaderId,
            visibilityScope(ownerType),
            folderId,
            uploaderId
        );
    }

    private String visibilityScope(String ownerType) {
        if ("IM_GROUP_FILE".equals(ownerType)) {
            return "CONVERSATION_MEMBER";
        }
        return "OWNER";
    }

    public int countActiveByFileObjectId(Long fileObjectId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_attachment_ref WHERE file_object_id = ? AND is_deleted = 0",
            Integer.class,
            fileObjectId
        );
        return count == null ? 0 : count;
    }

    public AttachmentRow getAttachmentOrThrow(Long attachmentRefId) {
        List<AttachmentRow> rows = jdbcTemplate.query("""
            SELECT ar.id, ar.file_object_id, ar.owner_type, ar.owner_id, ar.display_name, ar.uploader_type, ar.uploader_id,
                   ar.deleted_at, ar.purge_after, ar.created_at, ar.is_deleted,
                   fo.mime_type, fo.file_size, fo.content_sha256, fo.scan_status
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.id = ?
            """,
            (rs, rowNum) -> new AttachmentRow(
                rs.getLong("id"),
                rs.getLong("file_object_id"),
                rs.getString("owner_type"),
                rs.getObject("owner_id", Long.class),
                rs.getString("display_name"),
                rs.getString("uploader_type"),
                rs.getLong("uploader_id"),
                rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toLocalDateTime(),
                rs.getTimestamp("purge_after") == null ? null : rs.getTimestamp("purge_after").toLocalDateTime(),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getInt("is_deleted"),
                rs.getString("mime_type"),
                rs.getLong("file_size"),
                rs.getString("content_sha256"),
                rs.getString("scan_status")
            ),
            attachmentRefId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "附件引用不存在");
        }
        return rows.get(0);
    }

    public DownloadAttachmentRow getDownloadAttachmentOrThrow(Long attachmentRefId) {
        List<DownloadAttachmentRow> rows = jdbcTemplate.query("""
            SELECT ar.id AS attachment_ref_id, ar.owner_type, ar.display_name, ar.uploader_type, ar.uploader_id,
                   ar.deleted_at, ar.is_deleted AS attachment_deleted,
                   fo.storage_provider, fo.bucket_name, fo.object_key, fo.mime_type, fo.file_size,
                   fo.scan_status, fo.is_deleted AS file_deleted
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.id = ?
            """,
            (rs, rowNum) -> new DownloadAttachmentRow(
                rs.getLong("attachment_ref_id"),
                rs.getString("owner_type"),
                rs.getString("display_name"),
                rs.getString("uploader_type"),
                rs.getLong("uploader_id"),
                rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toLocalDateTime(),
                rs.getInt("attachment_deleted"),
                rs.getString("storage_provider"),
                rs.getString("bucket_name"),
                rs.getString("object_key"),
                rs.getString("mime_type"),
                rs.getLong("file_size"),
                rs.getString("scan_status"),
                rs.getInt("file_deleted")
            ),
            attachmentRefId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "附件引用不存在");
        }
        return rows.get(0);
    }
}
