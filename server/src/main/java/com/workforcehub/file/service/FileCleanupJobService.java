package com.workforcehub.file.service;

import com.workforcehub.common.audit.AuditLogWriter;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.common.util.TextValues;
import com.workforcehub.file.dto.CleanupJobRunResponse;
import com.workforcehub.file.dto.RunCleanupJobRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
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
    private static final Set<String> SUPPORTED_JOB_TYPES = Set.of(
        JOB_EXPIRED_UPLOAD_TICKET,
        JOB_EXPIRED_DOWNLOAD_TICKET,
        JOB_EXPIRED_RECYCLE_REF,
        JOB_UNREFERENCED_FILE_OBJECT,
        JOB_FAILED_PHYSICAL_OBJECT,
        JOB_ALL_EXPIRED
    );

    private final JdbcTemplate jdbcTemplate;
    private final FileCleanupDatabaseStageService databaseStageService;
    private final FileCleanupPhysicalService physicalService;
    private final TransactionTemplate transactionTemplate;
    private final AuditLogWriter auditLogWriter;

    public FileCleanupJobService(JdbcTemplate jdbcTemplate,
                                 FileCleanupDatabaseStageService databaseStageService,
                                 FileCleanupPhysicalService physicalService,
                                 TransactionTemplate transactionTemplate,
                                 AuditLogWriter auditLogWriter) {
        this.jdbcTemplate = jdbcTemplate;
        this.databaseStageService = databaseStageService;
        this.physicalService = physicalService;
        this.transactionTemplate = transactionTemplate;
        this.auditLogWriter = auditLogWriter;
    }

    public CleanupJobRunResponse runCleanupJob(RunCleanupJobRequest request) {
        Long adminId = CurrentUserHolder.requireUserId();
        String jobType = normalizeJobType(request.getJobType());
        boolean dryRun = Boolean.TRUE.equals(request.getDryRun());
        String jobRunId = "FILE-CLEAN-" + IdGenerator.nextId();
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
            physicalService.executePendingDeletes(jobRunId);
            stats = loadCleanupStats(jobRunId);
        }
        auditCleanupJob(adminId, jobRunId, jobType, dryRun, stats);
        return buildResponse(jobRunId, jobType, dryRun, stats);
    }

    private CleanupStats prepareCleanupJob(Long adminId, String jobRunId, String jobType, boolean dryRun, LocalDateTime now) {
        CleanupStats stats = new CleanupStats();
        if (shouldRun(jobType, JOB_EXPIRED_UPLOAD_TICKET)) {
            if (dryRun) {
                long expiredUploadTickets = databaseStageService.countExpiredUploadTickets(now);
                stats.expiredUploadTickets += expiredUploadTickets;
                stats.deletedPhysicalObjects += expiredUploadTickets;
            } else {
                stats.expiredUploadTickets += databaseStageService.expireUploadTickets(jobRunId, JOB_EXPIRED_UPLOAD_TICKET, now);
            }
        }
        if (shouldRun(jobType, JOB_EXPIRED_DOWNLOAD_TICKET)) {
            stats.expiredDownloadTickets += dryRun
                ? databaseStageService.countExpiredDownloadTickets(now)
                : databaseStageService.expireDownloadTickets(now);
        }
        if (shouldRun(jobType, JOB_EXPIRED_RECYCLE_REF)) {
            FileCleanupRecycleSummary summary;
            if (dryRun) {
                summary = databaseStageService.summarizeExpiredRecycleRefs(now);
            } else {
                summary = databaseStageService.purgeExpiredRecycleRefs(adminId, now);
            }
            stats.purgedAttachmentRefs += summary.count();
            stats.releasedBytes += summary.releasedBytes();
        }
        if (shouldRun(jobType, JOB_UNREFERENCED_FILE_OBJECT)) {
            if (dryRun) {
                stats.deletedPhysicalObjects += databaseStageService.countUnreferencedFileObjects(now);
            } else {
                databaseStageService.markUnreferencedFileObjectsForDeletion(jobRunId, JOB_UNREFERENCED_FILE_OBJECT, now);
            }
        }
        if (JOB_FAILED_PHYSICAL_OBJECT.equals(jobType)) {
            stats.deletedPhysicalObjects += dryRun
                ? physicalService.countRetryable(now)
                : physicalService.copyRetryableForRetry(jobRunId, now);
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

    private void auditCleanupJob(Long adminId, String jobRunId, String jobType, boolean dryRun, CleanupStats stats) {
        auditLogWriter.write(
            "ADMIN",
            adminId,
            "FILE_CLEANUP_JOB_RUN",
            "CLEANUP_JOB",
            null,
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
            IdGenerator.nextId(),
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
        stats.errorMessage = TextValues.safeSummary(errorMessage, "物理对象清理失败");
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

}
