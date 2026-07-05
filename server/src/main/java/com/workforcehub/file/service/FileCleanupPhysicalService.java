package com.workforcehub.file.service;

import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.common.util.TextValues;
import com.workforcehub.file.storage.StorageProviderRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FileCleanupPhysicalService {

    private static final String ITEM_DEDUPLICATED_UPLOAD_OBJECT = "DEDUPLICATED_UPLOAD_OBJECT";
    private static final int STALE_PENDING_MINUTES = 30;

    private final JdbcTemplate jdbcTemplate;
    private final StorageProviderRegistry storageProviderRegistry;

    public FileCleanupPhysicalService(JdbcTemplate jdbcTemplate,
                                      StorageProviderRegistry storageProviderRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageProviderRegistry = storageProviderRegistry;
    }

    public void createDetail(String jobRunId,
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
            IdGenerator.nextId(),
            jobRunId,
            itemType,
            targetType,
            targetId,
            storageProvider,
            bucketName,
            objectKey
        );
    }

    public long countRetryable(LocalDateTime now) {
        return findRetryablePhysicalCleanupDetails(now).size();
    }

    // 失败重试：把之前失败或超时的清理记录复制为新的待处理记录，标记原记录为失败
    public long copyRetryableForRetry(String jobRunId, LocalDateTime now) {
        List<RetryableCleanupDetailRow> rows = findRetryablePhysicalCleanupDetails(now);
        long copied = 0L;
        for (RetryableCleanupDetailRow row : rows) {
            if ("PENDING".equals(row.sourceStatus()) && !markRecoveredPendingDetailFailed(row)) {
                continue;
            }
            createDetail(
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

    // 执行物理删除：逐条调用存储提供方删除对象，成功/失败分别标记状态
    public void executePendingDeletes(String jobRunId) {
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

    // 查找可重试记录：FAILED 状态或 PENDING 超时（30分钟），排除已有后续操作的记录
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
            TextValues.safeSummary(errorMessage, "物理对象清理失败"),
            detailId
        );
    }

    private void finalizePhysicalCleanup(String jobRunId) {
        long deletedPhysicalObjects = countDetails(jobRunId, "SUCCESS");
        long failedPhysicalObjects = countDetails(jobRunId, "FAILED");
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

    private long countDetails(String jobRunId, String status) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_file_cleanup_job_detail
            WHERE job_run_id = ?
              AND status = ?
            """,
            Long.class,
            jobRunId,
            status
        );
        return count == null ? 0L : count;
    }

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
