package com.workforcehub.file.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// 数据库阶段清理：过期上传/下载票据、回收站过期引用、无引用文件对象
@Service
public class FileCleanupDatabaseStageService {

    private final JdbcTemplate jdbcTemplate;
    private final StorageUsageLedgerService storageUsageLedgerService;
    private final FileCleanupPhysicalService physicalService;

    public FileCleanupDatabaseStageService(JdbcTemplate jdbcTemplate,
                                           StorageUsageLedgerService storageUsageLedgerService,
                                           FileCleanupPhysicalService physicalService) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageUsageLedgerService = storageUsageLedgerService;
        this.physicalService = physicalService;
    }

    public long countExpiredUploadTickets(LocalDateTime now) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_upload_ticket
            WHERE status = 'PENDING' AND expire_at <= ? AND is_deleted = 0
            """, Long.class, now);
        return count == null ? 0L : count;
    }

    public long expireUploadTickets(String jobRunId, String itemType, LocalDateTime now) {
        List<UploadTicketCleanupRow> rows = jdbcTemplate.query("""
            SELECT id, storage_provider, bucket_name, object_key
            FROM sys_upload_ticket
            WHERE status = 'PENDING' AND expire_at <= ? AND is_deleted = 0
            """,
            (rs, rowNum) -> new UploadTicketCleanupRow(
                rs.getLong("id"),
                rs.getString("storage_provider"),
                rs.getString("bucket_name"),
                rs.getString("object_key")
            ),
            now
        );
        long expired = 0L;
        for (UploadTicketCleanupRow row : rows) {
            int updated = jdbcTemplate.update("""
                UPDATE sys_upload_ticket
                SET status = 'EXPIRED', updated_at = NOW()
                WHERE id = ? AND status = 'PENDING' AND is_deleted = 0
                """, row.id());
            if (updated == 1) {
                physicalService.createDetail(
                    jobRunId,
                    itemType,
                    "UPLOAD_TICKET",
                    row.id(),
                    row.storageProvider(),
                    row.bucketName(),
                    row.objectKey()
                );
                expired++;
            }
        }
        return expired;
    }

    public long countExpiredDownloadTickets(LocalDateTime now) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_download_ticket
            WHERE status = 'ACTIVE' AND expire_at <= ? AND is_deleted = 0
            """, Long.class, now);
        return count == null ? 0L : count;
    }

    public long expireDownloadTickets(LocalDateTime now) {
        return jdbcTemplate.update("""
            UPDATE sys_download_ticket
            SET status = 'EXPIRED', updated_at = NOW()
            WHERE status = 'ACTIVE' AND expire_at <= ? AND is_deleted = 0
            """, now);
    }

    public FileCleanupRecycleSummary summarizeExpiredRecycleRefs(LocalDateTime now) {
        return jdbcTemplate.query("""
            SELECT COUNT(*) AS ref_count, COALESCE(SUM(fo.file_size), 0) AS released_bytes
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.is_deleted = 0
              AND ar.deleted_at IS NOT NULL
              AND ar.purge_after <= ?
              AND ar.owner_type IN ('PERSONAL_FILE', 'ADMIN_FILE')
            """,
            rs -> {
                if (!rs.next()) {
                    return new FileCleanupRecycleSummary(0L, 0L);
                }
                return new FileCleanupRecycleSummary(rs.getLong("ref_count"), rs.getLong("released_bytes"));
            },
            now
        );
    }

    public FileCleanupRecycleSummary purgeExpiredRecycleRefs(Long adminId, LocalDateTime now) {
        List<RecycleRefCleanupRow> rows = jdbcTemplate.query("""
            SELECT ar.id, ar.owner_type, ar.uploader_id, ar.file_object_id, fo.file_size
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.is_deleted = 0
              AND ar.deleted_at IS NOT NULL
              AND ar.purge_after <= ?
              AND ar.owner_type IN ('PERSONAL_FILE', 'ADMIN_FILE')
            ORDER BY ar.id ASC
            """,
            (rs, rowNum) -> new RecycleRefCleanupRow(
                rs.getLong("id"),
                rs.getString("owner_type"),
                rs.getObject("uploader_id", Long.class),
                rs.getLong("file_object_id"),
                rs.getLong("file_size")
            ),
            now
        );
        long purgedAttachmentRefs = 0L;
        long releasedBytes = 0L;
        for (RecycleRefCleanupRow row : rows) {
            int updated = jdbcTemplate.update("""
                UPDATE sys_attachment_ref
                SET is_deleted = 1, updated_by = ?
                WHERE id = ? AND is_deleted = 0 AND deleted_at IS NOT NULL AND purge_after <= ?
                """, adminId, row.id(), now);
            if (updated == 1) {
                releaseUsage(row);
                refreshFileObjectCleanupState(row.fileObjectId(), now);
                purgedAttachmentRefs++;
                releasedBytes += row.fileSize();
            }
        }
        return new FileCleanupRecycleSummary(purgedAttachmentRefs, releasedBytes);
    }

    public long countUnreferencedFileObjects(LocalDateTime now) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_file_object fo
            WHERE fo.is_deleted = 0
              AND fo.cleanup_after IS NOT NULL
              AND fo.cleanup_after <= ?
              AND NOT EXISTS (
                  SELECT 1 FROM sys_attachment_ref ar
                  WHERE ar.file_object_id = fo.id AND ar.is_deleted = 0
              )
            """, Long.class, now);
        return count == null ? 0L : count;
    }

    public void markUnreferencedFileObjectsForDeletion(String jobRunId, String itemType, LocalDateTime now) {
        List<FileObjectCleanupRow> rows = jdbcTemplate.query("""
            SELECT fo.id, fo.storage_provider, fo.bucket_name, fo.object_key
            FROM sys_file_object fo
            WHERE fo.is_deleted = 0
              AND fo.cleanup_after IS NOT NULL
              AND fo.cleanup_after <= ?
              AND NOT EXISTS (
                  SELECT 1 FROM sys_attachment_ref ar
                  WHERE ar.file_object_id = fo.id AND ar.is_deleted = 0
              )
            ORDER BY fo.id ASC
            """,
            (rs, rowNum) -> new FileObjectCleanupRow(
                rs.getLong("id"),
                rs.getString("storage_provider"),
                rs.getString("bucket_name"),
                rs.getString("object_key")
            ),
            now
        );
        for (FileObjectCleanupRow row : rows) {
            int updated = jdbcTemplate.update("""
                UPDATE sys_file_object
                SET is_deleted = 1, cleanup_after = NULL
                WHERE id = ? AND is_deleted = 0
                  AND NOT EXISTS (
                      SELECT 1 FROM sys_attachment_ref ar
                      WHERE ar.file_object_id = sys_file_object.id AND ar.is_deleted = 0
                )
                """, row.id());
            if (updated == 1) {
                physicalService.createDetail(
                    jobRunId,
                    itemType,
                    "FILE_OBJECT",
                    row.id(),
                    row.storageProvider(),
                    row.bucketName(),
                    row.objectKey()
                );
            }
        }
    }

    private void releaseUsage(RecycleRefCleanupRow row) {
        if ("PERSONAL_FILE".equals(row.ownerType()) && row.uploaderId() != null) {
            storageUsageLedgerService.releaseUsage("USER", row.uploaderId(), row.fileSize(), 1);
            storageUsageLedgerService.releaseUsage("GLOBAL", null, row.fileSize(), 1);
            return;
        }
        if ("ADMIN_FILE".equals(row.ownerType())) {
            storageUsageLedgerService.releaseUsage("GLOBAL", null, row.fileSize(), 1);
        }
    }

    private void refreshFileObjectCleanupState(Long fileObjectId, LocalDateTime cleanupAfter) {
        Integer refCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_attachment_ref WHERE file_object_id = ? AND is_deleted = 0",
            Integer.class,
            fileObjectId
        );
        if (refCount == null || refCount == 0) {
            // 与手动彻底删除保持一致，业务引用清掉后保留 7 天物理清理缓冲。
            jdbcTemplate.update(
                "UPDATE sys_file_object SET ref_count = 0, cleanup_after = ? WHERE id = ? AND is_deleted = 0",
                cleanupAfter.plusDays(7),
                fileObjectId
            );
        } else {
            jdbcTemplate.update(
                "UPDATE sys_file_object SET ref_count = ? WHERE id = ? AND is_deleted = 0",
                refCount,
                fileObjectId
            );
        }
    }

    private record UploadTicketCleanupRow(
        Long id,
        String storageProvider,
        String bucketName,
        String objectKey
    ) {}

    private record RecycleRefCleanupRow(
        Long id,
        String ownerType,
        Long uploaderId,
        Long fileObjectId,
        Long fileSize
    ) {}

    private record FileObjectCleanupRow(
        Long id,
        String storageProvider,
        String bucketName,
        String objectKey
    ) {}
}

record FileCleanupRecycleSummary(long count, long releasedBytes) {}
