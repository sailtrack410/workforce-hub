package com.workforcehub.file.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.file.dto.CleanupJobRunResponse;
import com.workforcehub.file.dto.RunCleanupJobRequest;
import com.workforcehub.file.storage.StorageProviderRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
    private static final String JOB_FAILED_PHYSICAL_OBJECT = "FAILED_PHYSICAL_OBJECT";
    private static final String JOB_ALL_EXPIRED = "ALL_EXPIRED";
    private static final String ITEM_DEDUPLICATED_UPLOAD_OBJECT = "DEDUPLICATED_UPLOAD_OBJECT";
    private static final int STALE_PENDING_MINUTES = 30;
    private static final Set<String> SUPPORTED_JOB_TYPES = Set.of(
        JOB_EXPIRED_UPLOAD_TICKET,
        JOB_EXPIRED_DOWNLOAD_TICKET,
        JOB_EXPIRED_RECYCLE_REF,
        JOB_UNREFERENCED_FILE_OBJECT,
        JOB_FAILED_PHYSICAL_OBJECT,
        JOB_ALL_EXPIRED
    );

    private final JdbcTemplate jdbcTemplate;
    private final StorageProviderRegistry storageProviderRegistry;
    private final StorageUsageLedgerService storageUsageLedgerService;
    private final TransactionTemplate transactionTemplate;

    public FileCleanupJobService(JdbcTemplate jdbcTemplate,
                                 StorageProviderRegistry storageProviderRegistry,
                                 StorageUsageLedgerService storageUsageLedgerService,
                                 TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageProviderRegistry = storageProviderRegistry;
        this.storageUsageLedgerService = storageUsageLedgerService;
        this.transactionTemplate = transactionTemplate;
    }

    public CleanupJobRunResponse runCleanupJob(RunCleanupJobRequest request) {
        Long adminId = CurrentUserHolder.requireUserId();
        String jobType = normalizeJobType(request.getJobType());
        boolean dryRun = Boolean.TRUE.equals(request.getDryRun());
        String jobRunId = "FILE-CLEAN-" + IdWorker.getId();
        LocalDateTime now = LocalDateTime.now();

        createJobRun(adminId, jobRunId, jobType, dryRun, now);
        CleanupStats stats;
        try {
            stats = transactionTemplate.execute(status ->
                prepareCleanupJob(adminId, jobRunId, jobType, dryRun, now)
            );
        } catch (RuntimeException e) {
            stats = markJobRunFailed(jobRunId, e.getMessage());
        }
        if (stats == null) {
            stats = new CleanupStats();
        }
        if (!dryRun && "RUNNING".equals(stats.jobStatus)) {
            executePendingPhysicalDeletes(jobRunId);
            stats = loadCleanupStats(jobRunId);
        }
        auditCleanupJob(adminId, jobRunId, jobType, dryRun, stats);
        return buildResponse(jobRunId, jobType, dryRun, stats);
    }

    private CleanupStats prepareCleanupJob(Long adminId, String jobRunId, String jobType, boolean dryRun, LocalDateTime now) {
        CleanupStats stats = new CleanupStats();
        if (shouldRun(jobType, JOB_EXPIRED_UPLOAD_TICKET)) {
            if (dryRun) {
                long expiredUploadTickets = countExpiredUploadTickets(now);
                stats.expiredUploadTickets += expiredUploadTickets;
                stats.deletedPhysicalObjects += expiredUploadTickets;
            } else {
                stats.expiredUploadTickets += expireUploadTickets(jobRunId, now);
            }
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
            if (dryRun) {
                stats.deletedPhysicalObjects += countUnreferencedFileObjects(now);
            } else {
                markUnreferencedFileObjectsForDeletion(jobRunId, now);
            }
        }
        if (JOB_FAILED_PHYSICAL_OBJECT.equals(jobType)) {
            stats.deletedPhysicalObjects += dryRun
                ? countRetryablePhysicalCleanupDetails(now)
                : copyRetryablePhysicalCleanupDetailsForRetry(jobRunId, now);
        }

        stats.jobStatus = dryRun ? "SUCCESS" : "RUNNING";
        updateJobRunDatabaseStage(jobRunId, stats, dryRun);
        return stats;
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

    private long expireUploadTickets(String jobRunId, LocalDateTime now) {
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
                createCleanupDetail(
                    jobRunId,
                    JOB_EXPIRED_UPLOAD_TICKET,
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

    private void markUnreferencedFileObjectsForDeletion(String jobRunId, LocalDateTime now) {
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
                createCleanupDetail(
                    jobRunId,
                    JOB_UNREFERENCED_FILE_OBJECT,
                    "FILE_OBJECT",
                    row.id(),
                    row.storageProvider(),
                    row.bucketName(),
                    row.objectKey()
                );
            }
        }
    }

    private long countRetryablePhysicalCleanupDetails(LocalDateTime now) {
        return findRetryablePhysicalCleanupDetails(now).size();
    }

    private long copyRetryablePhysicalCleanupDetailsForRetry(String jobRunId, LocalDateTime now) {
        List<RetryableCleanupDetailRow> rows = findRetryablePhysicalCleanupDetails(now);
        long copied = 0L;
        for (RetryableCleanupDetailRow row : rows) {
            if ("PENDING".equals(row.sourceStatus()) && !markRecoveredPendingDetailFailed(row)) {
                continue;
            }
            createCleanupDetail(
                jobRunId,
                row.itemType(),
                row.targetType(),
                row.targetId(),
                row.storageProvider(),
                row.bucketName(),
                row.objectKey()
            );
            copied++;
        }
        markRecoveredPendingJobRunsFailed(rows);
        return copied;
    }

    private List<RetryableCleanupDetailRow> findRetryablePhysicalCleanupDetails(LocalDateTime now) {
        LocalDateTime staleBefore = now.minusMinutes(STALE_PENDING_MINUTES);
        return jdbcTemplate.query("""
            SELECT candidate.id,
                   candidate.job_run_id,
                   candidate.status,
                   candidate.item_type,
                   candidate.target_type,
                   candidate.target_id,
                   candidate.storage_provider,
                   candidate.bucket_name,
                   candidate.object_key
            FROM sys_file_cleanup_job_detail candidate
            LEFT JOIN sys_file_cleanup_job_run job_run
              ON job_run.job_run_id = candidate.job_run_id
            WHERE (
                  candidate.status = 'FAILED'
                  OR (
                      candidate.status = 'PENDING'
                      AND (
                          candidate.item_type = ?
                          OR (job_run.status = 'RUNNING' AND job_run.started_at <= ?)
                          OR (job_run.job_run_id IS NULL AND candidate.created_at <= ?)
                      )
                  )
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM sys_file_cleanup_job_detail later
                  WHERE later.id > candidate.id
                    AND later.status IN ('PENDING', 'SUCCESS', 'FAILED')
                    AND later.storage_provider = candidate.storage_provider
                    AND COALESCE(later.bucket_name, '') = COALESCE(candidate.bucket_name, '')
                    AND later.object_key = candidate.object_key
              )
            ORDER BY candidate.id ASC
            """,
            (rs, rowNum) -> new RetryableCleanupDetailRow(
                rs.getLong("id"),
                rs.getString("job_run_id"),
                rs.getString("status"),
                rs.getString("item_type"),
                rs.getString("target_type"),
                rs.getLong("target_id"),
                rs.getString("storage_provider"),
                rs.getString("bucket_name"),
                rs.getString("object_key")
            ),
            ITEM_DEDUPLICATED_UPLOAD_OBJECT,
            staleBefore,
            staleBefore
        );
    }

    private boolean markRecoveredPendingDetailFailed(RetryableCleanupDetailRow row) {
        int updated = jdbcTemplate.update("""
            UPDATE sys_file_cleanup_job_detail
            SET status = 'FAILED',
                error_message = ?,
                processed_at = NOW()
            WHERE id = ? AND status = 'PENDING'
            """,
            "清理任务中断或待回收对象进入失败重试",
            row.sourceDetailId()
        );
        return updated == 1;
    }

    private void markRecoveredPendingJobRunsFailed(List<RetryableCleanupDetailRow> rows) {
        List<String> jobRunIds = rows.stream()
            .filter(row -> "PENDING".equals(row.sourceStatus()))
            .map(RetryableCleanupDetailRow::sourceJobRunId)
            .filter(jobRunId -> jobRunId != null && !jobRunId.startsWith("FILE-CLEAN-DEDUP-"))
            .distinct()
            .toList();
        for (String jobRunId : jobRunIds) {
            jdbcTemplate.update("""
                UPDATE sys_file_cleanup_job_run
                SET status = 'FAILED',
                    error_message = ?,
                    finished_at = NOW()
                WHERE job_run_id = ? AND status = 'RUNNING'
                """,
                "清理任务超时未完成，已转入失败重试",
                jobRunId
            );
        }
    }

    private void auditCleanupJob(Long adminId, String jobRunId, String jobType, boolean dryRun, CleanupStats stats) {
        jdbcTemplate.update("""
            INSERT INTO sys_audit_log
                (id, operator_type, operator_id, action_type, target_type, target_id, result, detail_json)
            VALUES
                (?, 'ADMIN', ?, 'FILE_CLEANUP_JOB_RUN', 'CLEANUP_JOB', NULL, ?, CAST(? AS JSON))
            """,
            IdWorker.getId(),
            adminId,
            auditResult(stats.jobStatus),
            """
                {"jobRunId":"%s","jobType":"%s","dryRun":%s,"jobStatus":"%s","expiredUploadTickets":%d,"expiredDownloadTickets":%d,"purgedAttachmentRefs":%d,"deletedPhysicalObjects":%d,"failedPhysicalObjects":%d,"releasedBytes":%d,"errorMessage":"%s"}
                """.formatted(
                    jobRunId,
                    jobType,
                    dryRun,
                    stats.jobStatus,
                    stats.expiredUploadTickets,
                    stats.expiredDownloadTickets,
                    stats.purgedAttachmentRefs,
                    stats.deletedPhysicalObjects,
                    stats.failedPhysicalObjects,
                    stats.releasedBytes,
                    safeJsonText(stats.errorMessage)
                )
        );
    }

    private CleanupJobRunResponse buildResponse(String jobRunId, String jobType, boolean dryRun, CleanupStats stats) {
        CleanupJobRunResponse response = new CleanupJobRunResponse();
        response.setJobRunId(jobRunId);
        response.setJobType(jobType);
        response.setDryRun(dryRun);
        response.setJobStatus(stats.jobStatus);
        response.setExpiredUploadTickets(stats.expiredUploadTickets);
        response.setExpiredDownloadTickets(stats.expiredDownloadTickets);
        response.setPurgedAttachmentRefs(stats.purgedAttachmentRefs);
        response.setDeletedPhysicalObjects(stats.deletedPhysicalObjects);
        response.setFailedPhysicalObjects(stats.failedPhysicalObjects);
        response.setReleasedBytes(stats.releasedBytes);
        response.setErrorMessage(stats.errorMessage);
        return response;
    }

    private static class CleanupStats {
        private String jobStatus = "SUCCESS";
        private long expiredUploadTickets;
        private long expiredDownloadTickets;
        private long purgedAttachmentRefs;
        private long deletedPhysicalObjects;
        private long failedPhysicalObjects;
        private long releasedBytes;
        private String errorMessage;
    }

    private void createJobRun(Long adminId, String jobRunId, String jobType, boolean dryRun, LocalDateTime now) {
        jdbcTemplate.update("""
            INSERT INTO sys_file_cleanup_job_run
                (id, job_run_id, job_type, dry_run, status, operator_id, started_at)
            VALUES
                (?, ?, ?, ?, 'RUNNING', ?, ?)
            """,
            IdWorker.getId(),
            jobRunId,
            jobType,
            dryRun ? 1 : 0,
            adminId,
            now
        );
    }

    private void updateJobRunDatabaseStage(String jobRunId, CleanupStats stats, boolean finished) {
        jdbcTemplate.update("""
            UPDATE sys_file_cleanup_job_run
            SET status = ?,
                expired_upload_tickets = ?,
                expired_download_tickets = ?,
                purged_attachment_refs = ?,
                deleted_physical_objects = ?,
                failed_physical_objects = ?,
                released_bytes = ?,
                error_message = ?,
                finished_at = CASE WHEN ? THEN NOW() ELSE finished_at END
            WHERE job_run_id = ?
            """,
            stats.jobStatus,
            stats.expiredUploadTickets,
            stats.expiredDownloadTickets,
            stats.purgedAttachmentRefs,
            stats.deletedPhysicalObjects,
            stats.failedPhysicalObjects,
            stats.releasedBytes,
            stats.errorMessage,
            finished,
            jobRunId
        );
    }

    private CleanupStats markJobRunFailed(String jobRunId, String errorMessage) {
        CleanupStats stats = loadCleanupStats(jobRunId);
        stats.jobStatus = "FAILED";
        stats.errorMessage = trimError(errorMessage);
        jdbcTemplate.update("""
            UPDATE sys_file_cleanup_job_run
            SET status = 'FAILED',
                error_message = ?,
                finished_at = NOW()
            WHERE job_run_id = ?
            """,
            stats.errorMessage,
            jobRunId
        );
        return loadCleanupStats(jobRunId);
    }

    private void createCleanupDetail(String jobRunId,
                                     String itemType,
                                     String targetType,
                                     Long targetId,
                                     String storageProvider,
                                     String bucketName,
                                     String objectKey) {
        jdbcTemplate.update("""
            INSERT INTO sys_file_cleanup_job_detail
                (id, job_run_id, item_type, target_type, target_id, storage_provider, bucket_name, object_key, status)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
            """,
            IdWorker.getId(),
            jobRunId,
            itemType,
            targetType,
            targetId,
            storageProvider,
            bucketName,
            objectKey
        );
    }

    private void executePendingPhysicalDeletes(String jobRunId) {
        List<PhysicalCleanupItem> items = jdbcTemplate.query("""
            SELECT id, item_type, storage_provider, bucket_name, object_key
            FROM sys_file_cleanup_job_detail
            WHERE job_run_id = ? AND status = 'PENDING'
            ORDER BY id ASC
            """,
            (rs, rowNum) -> new PhysicalCleanupItem(
                rs.getLong("id"),
                rs.getString("item_type"),
                rs.getString("storage_provider"),
                rs.getString("bucket_name"),
                rs.getString("object_key")
            ),
            jobRunId
        );
        for (PhysicalCleanupItem item : items) {
            try {
                storageProviderRegistry.require(item.storageProvider()).deleteObject(item.bucketName(), item.objectKey());
                markCleanupDetailSuccess(item.id());
            } catch (RuntimeException e) {
                markCleanupDetailFailed(item.id(), e.getMessage());
            }
        }
        finalizePhysicalCleanup(jobRunId);
    }

    private void markCleanupDetailSuccess(Long detailId) {
        jdbcTemplate.update("""
            UPDATE sys_file_cleanup_job_detail
            SET status = 'SUCCESS',
                attempt_count = attempt_count + 1,
                error_message = NULL,
                processed_at = NOW()
            WHERE id = ? AND status = 'PENDING'
            """, detailId);
    }

    private void markCleanupDetailFailed(Long detailId, String errorMessage) {
        jdbcTemplate.update("""
            UPDATE sys_file_cleanup_job_detail
            SET status = 'FAILED',
                attempt_count = attempt_count + 1,
                error_message = ?,
                processed_at = NOW()
            WHERE id = ? AND status = 'PENDING'
            """,
            trimError(errorMessage),
            detailId
        );
    }

    private void finalizePhysicalCleanup(String jobRunId) {
        long deletedPhysicalObjects = countCleanupDetails(jobRunId, null, "SUCCESS");
        long failedPhysicalObjects = countCleanupDetails(jobRunId, null, "FAILED");
        String jobStatus = failedPhysicalObjects > 0 ? "PARTIAL_FAILED" : "SUCCESS";
        jdbcTemplate.update("""
            UPDATE sys_file_cleanup_job_run
            SET status = ?,
                deleted_physical_objects = ?,
                failed_physical_objects = ?,
                finished_at = NOW()
            WHERE job_run_id = ?
            """,
            jobStatus,
            deletedPhysicalObjects,
            failedPhysicalObjects,
            jobRunId
        );
    }

    private long countCleanupDetails(String jobRunId, String itemType, String status) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_file_cleanup_job_detail
            WHERE job_run_id = ?
              AND (? IS NULL OR item_type = ?)
              AND status = ?
            """,
            Long.class,
            jobRunId,
            itemType,
            itemType,
            status
        );
        return count == null ? 0L : count;
    }

    private CleanupStats loadCleanupStats(String jobRunId) {
        return jdbcTemplate.query("""
            SELECT status, expired_upload_tickets, expired_download_tickets, purged_attachment_refs,
                   deleted_physical_objects, failed_physical_objects, released_bytes, error_message
            FROM sys_file_cleanup_job_run
            WHERE job_run_id = ?
            """,
            rs -> {
                if (!rs.next()) {
                    return new CleanupStats();
                }
                CleanupStats stats = new CleanupStats();
                stats.jobStatus = rs.getString("status");
                stats.expiredUploadTickets = rs.getLong("expired_upload_tickets");
                stats.expiredDownloadTickets = rs.getLong("expired_download_tickets");
                stats.purgedAttachmentRefs = rs.getLong("purged_attachment_refs");
                stats.deletedPhysicalObjects = rs.getLong("deleted_physical_objects");
                stats.failedPhysicalObjects = rs.getLong("failed_physical_objects");
                stats.releasedBytes = rs.getLong("released_bytes");
                stats.errorMessage = rs.getString("error_message");
                return stats;
            },
            jobRunId
        );
    }

    private String auditResult(String jobStatus) {
        if ("FAILED".equals(jobStatus) || "PARTIAL_FAILED".equals(jobStatus)) {
            return "FAIL";
        }
        return "SUCCESS";
    }

    private String trimError(String message) {
        if (message == null || message.isBlank()) {
            return "物理对象清理失败";
        }
        return message.length() > 512 ? message.substring(0, 512) : message;
    }

    private String safeJsonText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
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

    private record PhysicalCleanupItem(
        Long id,
        String itemType,
        String storageProvider,
        String bucketName,
        String objectKey
    ) {}

    private record RetryableCleanupDetailRow(
        Long sourceDetailId,
        String sourceJobRunId,
        String sourceStatus,
        String itemType,
        String targetType,
        Long targetId,
        String storageProvider,
        String bucketName,
        String objectKey
    ) {}
}
