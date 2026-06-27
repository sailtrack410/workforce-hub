package com.workforcehub.agent.repository;

import com.workforcehub.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 人工确认超时扫描、批次记录和任务锁仓储。
 */
@Repository
public class AgentTaskTimeoutRepository {

    private final JdbcTemplate jdbcTemplate;

    public AgentTaskTimeoutRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AgentExpiredTaskRow> listExpiredWaitingTasks(LocalDateTime now, int limit) {
        return jdbcTemplate.query("""
            SELECT id, context_type, context_id
            FROM agt_task
            WHERE status = 'WAITING_HUMAN_CONFIRM'
              AND waiting_deadline_at IS NOT NULL
              AND waiting_deadline_at <= ?
            ORDER BY waiting_deadline_at ASC, id ASC
            LIMIT ?
            """, (rs, rowNum) -> new AgentExpiredTaskRow(
                rs.getLong("id"),
                rs.getString("context_type"),
                rs.getObject("context_id", Long.class)
            ), now, limit);
    }

    public boolean cancelExpiredWaitingTask(Long taskId, LocalDateTime now, String responsePayloadJson) {
        int taskUpdated = jdbcTemplate.update("""
            UPDATE agt_task
            SET status = 'CANCELLED',
                finished_at = NOW(),
                error_message = '人工确认超时，任务已取消',
                updated_by = NULL
            WHERE id = ?
              AND status = 'WAITING_HUMAN_CONFIRM'
              AND waiting_deadline_at IS NOT NULL
              AND waiting_deadline_at <= ?
            """, taskId, now);
        if (taskUpdated != 1) {
            return false;
        }
        jdbcTemplate.update("""
            UPDATE agt_tool_call_log
            SET approval_status = 'TIMEOUT',
                result_status = 'CANCELLED',
                response_payload_json = CAST(? AS JSON)
            WHERE task_id = ?
              AND approval_status = 'PENDING'
              AND result_status = 'WAITING_CONFIRM'
            """, responsePayloadJson, taskId);
        return true;
    }

    public void createTaskTimeoutJobRun(String jobRunId,
                                        String triggerType,
                                        int batchSize,
                                        String lockOwner,
                                        LocalDateTime startedAt) {
        jdbcTemplate.update("""
            INSERT INTO agt_task_timeout_job_run
                (id, job_run_id, trigger_type, status, batch_size, lock_owner, started_at)
            VALUES
                (?, ?, ?, 'RUNNING', ?, ?, ?)
            """,
            IdGenerator.nextId(),
            jobRunId,
            triggerType,
            batchSize,
            lockOwner,
            startedAt
        );
    }

    public void finishTaskTimeoutJobRun(String jobRunId,
                                        String status,
                                        long scannedCount,
                                        long cancelledCount,
                                        long skippedCount,
                                        String errorMessage) {
        jdbcTemplate.update("""
            UPDATE agt_task_timeout_job_run
            SET status = ?,
                scanned_count = ?,
                cancelled_count = ?,
                skipped_count = ?,
                error_message = ?,
                finished_at = NOW()
            WHERE job_run_id = ?
            """,
            status,
            scannedCount,
            cancelledCount,
            skippedCount,
            errorMessage,
            jobRunId
        );
    }

    public boolean tryAcquireJobLock(String lockName, String ownerId, LocalDateTime now, LocalDateTime lockedUntil) {
        jdbcTemplate.update("""
            INSERT INTO sys_job_lock
                (lock_name, owner_id, locked_at, locked_until)
            VALUES
                (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                owner_id = IF(locked_until <= ?, ?, owner_id),
                locked_at = IF(locked_until <= ?, ?, locked_at),
                locked_until = IF(locked_until <= ?, ?, locked_until)
            """,
            lockName,
            ownerId,
            now,
            lockedUntil,
            now,
            ownerId,
            now,
            now,
            now,
            lockedUntil
        );
        String currentOwner = jdbcTemplate.queryForObject(
            "SELECT owner_id FROM sys_job_lock WHERE lock_name = ?",
            String.class,
            lockName
        );
        return ownerId.equals(currentOwner);
    }

    public void releaseJobLock(String lockName, String ownerId, LocalDateTime now) {
        jdbcTemplate.update("""
            UPDATE sys_job_lock
            SET locked_until = ?,
                updated_at = NOW()
            WHERE lock_name = ?
              AND owner_id = ?
            """, now, lockName, ownerId);
    }

    public long countAdminAgentTaskTimeoutRuns(AgentTaskTimeoutRunQueryCriteria criteria) {
        List<Object> params = new ArrayList<>();
        String sql = """
            SELECT COUNT(*)
            FROM agt_task_timeout_job_run run
            """ + timeoutRunWhereSql(criteria, params);
        Long count = jdbcTemplate.queryForObject(sql, Long.class, params.toArray());
        return count == null ? 0L : count;
    }

    public List<AdminAgentTaskTimeoutRunRow> pageAdminAgentTaskTimeoutRuns(AgentTaskTimeoutRunQueryCriteria criteria,
                                                                          long offset,
                                                                          long limit) {
        List<Object> params = new ArrayList<>();
        String sql = """
            SELECT job_run_id,
                   trigger_type,
                   status,
                   batch_size,
                   scanned_count,
                   cancelled_count,
                   skipped_count,
                   lock_owner,
                   error_message,
                   started_at,
                   finished_at,
                   created_at,
                   updated_at
            FROM agt_task_timeout_job_run run
            """ + timeoutRunWhereSql(criteria, params) + """
            ORDER BY started_at DESC, id DESC
            LIMIT ? OFFSET ?
            """;
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, this::mapAdminTaskTimeoutRun, params.toArray());
    }

    private String timeoutRunWhereSql(AgentTaskTimeoutRunQueryCriteria criteria, List<Object> params) {
        StringBuilder where = new StringBuilder("""
            WHERE 1 = 1
            """);
        if (criteria.triggerType() != null) {
            where.append("  AND run.trigger_type = ?\n");
            params.add(criteria.triggerType());
        }
        if (criteria.status() != null) {
            where.append("  AND run.status = ?\n");
            params.add(criteria.status());
        }
        if (criteria.startedFrom() != null) {
            where.append("  AND run.started_at >= ?\n");
            params.add(criteria.startedFrom());
        }
        if (criteria.startedTo() != null) {
            where.append("  AND run.started_at <= ?\n");
            params.add(criteria.startedTo());
        }
        return where.toString();
    }

    private AdminAgentTaskTimeoutRunRow mapAdminTaskTimeoutRun(ResultSet rs, int rowNum) throws SQLException {
        return new AdminAgentTaskTimeoutRunRow(
            rs.getString("job_run_id"),
            rs.getString("trigger_type"),
            rs.getString("status"),
            rs.getInt("batch_size"),
            rs.getLong("scanned_count"),
            rs.getLong("cancelled_count"),
            rs.getLong("skipped_count"),
            rs.getString("lock_owner"),
            rs.getString("error_message"),
            rs.getTimestamp("started_at").toLocalDateTime(),
            rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toLocalDateTime(),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}
