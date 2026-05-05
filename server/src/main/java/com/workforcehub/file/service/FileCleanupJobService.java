package com.workforcehub.file.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.file.dto.CleanupJobRunResponse;
import com.workforcehub.file.dto.RunCleanupJobRequest;
import com.workforcehub.file.storage.StorageProviderRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 文件中心清理任务服务，集中处理过期票据、到期回收站引用和无引用物理对象。
 */
@Service
public class FileCleanupJobService {

    private static final String JOB_EXPIRED_UPLOAD_TICKET = "EXPIRED_UPLOAD_TICKET";
    private static final String JOB_EXPIRED_DOWNLOAD_TICKET = "EXPIRED_DOWNLOAD_TICKET";
    private static final String JOB_EXPIRED_RECYCLE_REF = "EXPIRED_RECYCLE_REF";
    private static final String JOB_UNREFERENCED_FILE_OBJECT = "UNREFERENCED_FILE_OBJECT";
    private static final String JOB_ALL_EXPIRED = "ALL_EXPIRED";
    private static final Set<String> SUPPORTED_JOB_TYPES = Set.of(
        JOB_EXPIRED_UPLOAD_TICKET,
        JOB_EXPIRED_DOWNLOAD_TICKET,
        JOB_EXPIRED_RECYCLE_REF,
        JOB_UNREFERENCED_FILE_OBJECT,
        JOB_ALL_EXPIRED
    );

    private final JdbcTemplate jdbcTemplate;
    private final StorageProviderRegistry storageProviderRegistry;
    private final StorageUsageLedgerService storageUsageLedgerService;

    public FileCleanupJobService(JdbcTemplate jdbcTemplate,
                                 StorageProviderRegistry storageProviderRegistry,
                                 StorageUsageLedgerService storageUsageLedgerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageProviderRegistry = storageProviderRegistry;
        this.storageUsageLedgerService = storageUsageLedgerService;
    }

    @Transactional(rollbackFor = Exception.class)
    public CleanupJobRunResponse runCleanupJob(RunCleanupJobRequest request) {
        Long adminId = CurrentUserHolder.requireUserId();
        String jobType = normalizeJobType(request.getJobType());
        boolean dryRun = Boolean.TRUE.equals(request.getDryRun());
        String jobRunId = "FILE-CLEAN-" + IdWorker.getId();
        LocalDateTime now = LocalDateTime.now();

        CleanupStats stats = new CleanupStats();
        if (shouldRun(jobType, JOB_EXPIRED_UPLOAD_TICKET)) {
            stats.expiredUploadTickets += dryRun
                ? countExpiredUploadTickets(now)
                : expireUploadTickets(now);
        }
        if (shouldRun(jobType, JOB_EXPIRED_DOWNLOAD_TICKET)) {
            stats.expiredDownloadTickets += dryRun
                ? countExpiredDownloadTickets(now)
                : expireDownloadTickets(now);
        }
        if (shouldRun(jobType, JOB_EXPIRED_RECYCLE_REF)) {
            if (dryRun) {
                RecycleSummary summary = summarizeExpiredRecycleRefs(now);
                stats.purgedAttachmentRefs += summary.count();
                stats.releasedBytes += summary.releasedBytes();
            } else {
                purgeExpiredRecycleRefs(adminId, now, stats);
            }
        }
        if (shouldRun(jobType, JOB_UNREFERENCED_FILE_OBJECT)) {
            stats.deletedPhysicalObjects += dryRun
                ? countUnreferencedFileObjects(now)
                : deleteUnreferencedFileObjects(now);
        }

        auditCleanupJob(adminId, jobRunId, jobType, dryRun, stats);
        return buildResponse(jobRunId, jobType, dryRun, stats);
    }

    private String normalizeJobType(String jobType) {
        String normalized = jobType == null ? "" : jobType.trim().toUpperCase();
        if (!SUPPORTED_JOB_TYPES.contains(normalized)) {
            throw new BizException(400, "不支持的清理任务类型");
        }
        return normalized;
    }

    private boolean shouldRun(String requestedJobType, String targetJobType) {
        return JOB_ALL_EXPIRED.equals(requestedJobType) || targetJobType.equals(requestedJobType);
    }

    private long countExpiredUploadTickets(LocalDateTime now) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_upload_ticket
            WHERE status = 'PENDING' AND expire_at <= ? AND is_deleted = 0
            """, Long.class, now);
        return count == null ? 0L : count;
    }

    private long expireUploadTickets(LocalDateTime now) {
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
                storageProviderRegistry.require(row.storageProvider()).deleteObject(row.bucketName(), row.objectKey());
                expired++;
            }
        }
        return expired;
    }

    private long countExpiredDownloadTickets(LocalDateTime now) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_download_ticket
            WHERE status = 'ACTIVE' AND expire_at <= ? AND is_deleted = 0
            """, Long.class, now);
        return count == null ? 0L : count;
    }

    private long expireDownloadTickets(LocalDateTime now) {
        return jdbcTemplate.update("""
            UPDATE sys_download_ticket
            SET status = 'EXPIRED', updated_at = NOW()
            WHERE status = 'ACTIVE' AND expire_at <= ? AND is_deleted = 0
            """, now);
    }

    private RecycleSummary summarizeExpiredRecycleRefs(LocalDateTime now) {
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
                    return new RecycleSummary(0L, 0L);
                }
                return new RecycleSummary(rs.getLong("ref_count"), rs.getLong("released_bytes"));
            },
            now
        );
    }

    private void purgeExpiredRecycleRefs(Long adminId, LocalDateTime now, CleanupStats stats) {
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
        for (RecycleRefCleanupRow row : rows) {
            int updated = jdbcTemplate.update("""
                UPDATE sys_attachment_ref
                SET is_deleted = 1, updated_by = ?
                WHERE id = ? AND is_deleted = 0 AND deleted_at IS NOT NULL AND purge_after <= ?
                """, adminId, row.id(), now);
            if (updated == 1) {
                releaseUsage(row);
                refreshFileObjectCleanupState(row.fileObjectId(), now);
                stats.purgedAttachmentRefs++;
                stats.releasedBytes += row.fileSize();
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

    private long countUnreferencedFileObjects(LocalDateTime now) {
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

    private long deleteUnreferencedFileObjects(LocalDateTime now) {
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
        long deleted = 0L;
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
                storageProviderRegistry.require(row.storageProvider()).deleteObject(row.bucketName(), row.objectKey());
                deleted++;
            }
        }
        return deleted;
    }

    private void auditCleanupJob(Long adminId, String jobRunId, String jobType, boolean dryRun, CleanupStats stats) {
        jdbcTemplate.update("""
            INSERT INTO sys_audit_log
                (id, operator_type, operator_id, action_type, target_type, target_id, result, detail_json)
            VALUES
                (?, 'ADMIN', ?, 'FILE_CLEANUP_JOB_RUN', 'CLEANUP_JOB', NULL, 'SUCCESS', CAST(? AS JSON))
            """,
            IdWorker.getId(),
            adminId,
            """
                {"jobRunId":"%s","jobType":"%s","dryRun":%s,"expiredUploadTickets":%d,"expiredDownloadTickets":%d,"purgedAttachmentRefs":%d,"deletedPhysicalObjects":%d,"releasedBytes":%d}
                """.formatted(
                    jobRunId,
                    jobType,
                    dryRun,
                    stats.expiredUploadTickets,
                    stats.expiredDownloadTickets,
                    stats.purgedAttachmentRefs,
                    stats.deletedPhysicalObjects,
                    stats.releasedBytes
                )
        );
    }

    private CleanupJobRunResponse buildResponse(String jobRunId, String jobType, boolean dryRun, CleanupStats stats) {
        CleanupJobRunResponse response = new CleanupJobRunResponse();
        response.setJobRunId(jobRunId);
        response.setJobType(jobType);
        response.setDryRun(dryRun);
        response.setExpiredUploadTickets(stats.expiredUploadTickets);
        response.setExpiredDownloadTickets(stats.expiredDownloadTickets);
        response.setPurgedAttachmentRefs(stats.purgedAttachmentRefs);
        response.setDeletedPhysicalObjects(stats.deletedPhysicalObjects);
        response.setReleasedBytes(stats.releasedBytes);
        return response;
    }

    private static class CleanupStats {
        private long expiredUploadTickets;
        private long expiredDownloadTickets;
        private long purgedAttachmentRefs;
        private long deletedPhysicalObjects;
        private long releasedBytes;
    }

    private record UploadTicketCleanupRow(
        Long id,
        String storageProvider,
        String bucketName,
        String objectKey
    ) {}

    private record RecycleSummary(long count, long releasedBytes) {}

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
