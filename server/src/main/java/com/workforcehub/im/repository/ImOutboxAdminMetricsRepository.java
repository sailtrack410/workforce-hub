package com.workforcehub.im.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * IM outbox 后台指标仓储。
 */
@Repository
public class ImOutboxAdminMetricsRepository {

    private final JdbcTemplate jdbcTemplate;

    public ImOutboxAdminMetricsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdminImOutboxStatusSummaryRow> countOutboxEventsByStatus() {
        return jdbcTemplate.query("""
            SELECT status,
                   COUNT(*) AS event_count
            FROM im_event_outbox
            GROUP BY status
            """,
            (rs, rowNum) -> new AdminImOutboxStatusSummaryRow(
                rs.getString("status"),
                rs.getLong("event_count")
            )
        );
    }

    public AdminImOutboxSummaryAggregateRow summarizeOutboxEvents(LocalDateTime now,
                                                                  LocalDateTime staleSendingBefore) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*) AS total_count,
                   SUM(CASE
                           WHEN status IN ('PENDING', 'FAILED') AND next_retry_at <= ? THEN 1
                           WHEN status = 'SENDING' AND claimed_at < ? THEN 1
                           ELSE 0
                       END) AS due_dispatch_count,
                   SUM(CASE
                           WHEN status = 'SENDING' AND claimed_at < ? THEN 1
                           ELSE 0
                       END) AS stale_sending_count,
                   MIN(created_at) AS earliest_created_at,
                   MIN(CASE
                           WHEN status IN ('PENDING', 'FAILED') THEN next_retry_at
                           ELSE NULL
                       END) AS earliest_next_retry_at
            FROM im_event_outbox
            """,
            (rs, rowNum) -> new AdminImOutboxSummaryAggregateRow(
                rs.getLong("total_count"),
                rs.getLong("due_dispatch_count"),
                rs.getLong("stale_sending_count"),
                nullableTimestamp(rs, "earliest_created_at"),
                nullableTimestamp(rs, "earliest_next_retry_at")
            ),
            Timestamp.valueOf(now),
            Timestamp.valueOf(staleSendingBefore),
            Timestamp.valueOf(staleSendingBefore)
        );
    }

    public List<AdminImOutboxFailureSummaryRow> listOutboxFailureSummary(int limit) {
        return jdbcTemplate.query("""
            SELECT status,
                   COALESCE(NULLIF(last_error, ''), 'UNKNOWN') AS last_error,
                   COUNT(*) AS event_count,
                   MAX(retry_count) AS max_retry_count,
                   MIN(COALESCE(updated_at, created_at)) AS first_occurred_at,
                   MAX(COALESCE(updated_at, created_at)) AS last_occurred_at
            FROM im_event_outbox
            WHERE status IN ('FAILED', 'DEAD')
            GROUP BY status, COALESCE(NULLIF(last_error, ''), 'UNKNOWN')
            ORDER BY event_count DESC, last_occurred_at DESC, last_error ASC
            LIMIT ?
            """,
            (rs, rowNum) -> new AdminImOutboxFailureSummaryRow(
                rs.getString("status"),
                rs.getString("last_error"),
                rs.getLong("event_count"),
                rs.getInt("max_retry_count"),
                nullableTimestamp(rs, "first_occurred_at"),
                nullableTimestamp(rs, "last_occurred_at")
            ),
            limit
        );
    }

    private LocalDateTime nullableTimestamp(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
