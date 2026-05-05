package com.workforcehub.file.repository;

import com.workforcehub.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class FileObjectRepository {

    private final JdbcTemplate jdbcTemplate;

    public FileObjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public FileObjectRow getFileObjectOrThrow(Long fileObjectId) {
        List<FileObjectRow> rows = jdbcTemplate.query("""
            SELECT id, storage_provider, bucket_name, object_key, file_size, content_sha256
            FROM sys_file_object
            WHERE id = ? AND is_deleted = 0
            """,
            (rs, rowNum) -> mapFileObject(rs),
            fileObjectId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "文件对象不存在");
        }
        return rows.get(0);
    }

    public FileObjectRow findActiveBySha256AndSize(String contentSha256, Long fileSize) {
        List<FileObjectRow> rows = jdbcTemplate.query("""
            SELECT id, storage_provider, bucket_name, object_key, file_size, content_sha256
            FROM sys_file_object
            WHERE content_sha256 = ? AND file_size = ? AND is_deleted = 0
            LIMIT 1
            """,
            (rs, rowNum) -> mapFileObject(rs),
            contentSha256.toLowerCase(),
            fileSize
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public FileObjectRow findReusableForUploader(String uploaderType,
                                                 Long uploaderId,
                                                 String contentSha256,
                                                 Long fileSize) {
        List<FileObjectRow> rows = jdbcTemplate.query("""
            SELECT fo.id, fo.storage_provider, fo.bucket_name, fo.object_key, fo.file_size, fo.content_sha256
            FROM sys_file_object fo
            JOIN sys_attachment_ref ar ON ar.file_object_id = fo.id
            WHERE fo.content_sha256 = ?
              AND fo.file_size = ?
              AND fo.is_deleted = 0
              AND ar.uploader_type = ?
              AND ar.uploader_id = ?
              AND ar.deleted_at IS NULL
              AND ar.is_deleted = 0
            ORDER BY ar.created_at DESC, ar.id DESC
            LIMIT 1
            """,
            (rs, rowNum) -> mapFileObject(rs),
            contentSha256.toLowerCase(),
            fileSize,
            uploaderType,
            uploaderId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void increaseRefCount(Long fileObjectId) {
        jdbcTemplate.update("UPDATE sys_file_object SET ref_count = ref_count + 1 WHERE id = ?", fileObjectId);
    }

    public boolean refreshRefCountAndCleanupAfter(Long fileObjectId, int activeRefCount, LocalDateTime cleanupAfter) {
        boolean cleanupScheduled = activeRefCount <= 0;
        if (cleanupScheduled) {
            jdbcTemplate.update(
                "UPDATE sys_file_object SET ref_count = 0, cleanup_after = ? WHERE id = ?",
                cleanupAfter,
                fileObjectId
            );
            return true;
        }
        jdbcTemplate.update("UPDATE sys_file_object SET ref_count = ? WHERE id = ?", activeRefCount, fileObjectId);
        return false;
    }

    private FileObjectRow mapFileObject(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FileObjectRow(
            rs.getLong("id"),
            rs.getString("storage_provider"),
            rs.getString("bucket_name"),
            rs.getString("object_key"),
            rs.getLong("file_size"),
            rs.getString("content_sha256")
        );
    }
}
