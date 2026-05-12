package com.workforcehub.im.repository;

import com.workforcehub.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * IM outbox 后台事件读写仓储。
 */
@Repository
public class ImOutboxAdminEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public ImOutboxAdminEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countOutboxEvents(AdminImOutboxEventQueryCriteria criteria) {
        List<Object> params = new ArrayList<>();
        String sql = """
            SELECT COUNT(*)
            FROM im_event_outbox
            """ + outboxWhereSql(criteria, params);
        Long count = jdbcTemplate.queryForObject(sql, Long.class, params.toArray());
        return count == null ? 0L : count;
    }

    public List<AdminImOutboxEventRow> pageOutboxEvents(AdminImOutboxEventQueryCriteria criteria,
                                                        long offset,
                                                        long limit) {
        List<Object> params = new ArrayList<>();
        String sql = outboxSelectSql()
            + outboxWhereSql(criteria, params)
            + """
            ORDER BY created_at DESC, id DESC
            LIMIT ? OFFSET ?
            """;
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, this::mapOutboxEvent, params.toArray());
    }

    public AdminImOutboxEventRow getOutboxEventOrThrow(Long eventId) {
        return findOutboxEvent(eventId).orElseThrow(() -> new BizException(404, "IM outbox事件不存在"));
    }

    public Optional<AdminImOutboxEventRow> findOutboxEvent(Long eventId) {
        List<AdminImOutboxEventRow> rows = jdbcTemplate.query(outboxSelectSql() + """
            WHERE id = ?
            LIMIT 1
            """, this::mapOutboxEvent, eventId);
        return rows.stream().findFirst();
    }

    public int resetForAdminRetry(Long eventId, LocalDateTime nextRetryAt) {
        return jdbcTemplate.update("""
            UPDATE im_event_outbox
            SET status = 'FAILED',
                next_retry_at = ?,
                claimed_at = NULL,
                sent_at = NULL,
                last_error = NULL
            WHERE id = ?
              AND status IN ('FAILED', 'DEAD')
            """,
            Timestamp.valueOf(nextRetryAt),
            eventId
        );
    }

    private String outboxSelectSql() {
        return """
            SELECT id,
                   event_type,
                   conversation_id,
                   message_id,
                   seq_no,
                   actor_user_id,
                   CAST(recipient_user_ids_json AS CHAR) AS recipient_user_ids_json,
                   CAST(payload_json AS CHAR) AS payload_json,
                   status,
                   retry_count,
                   next_retry_at,
                   last_error,
                   claimed_at,
                   sent_at,
                   created_at,
                   updated_at
            FROM im_event_outbox
            """;
    }

    private String outboxWhereSql(AdminImOutboxEventQueryCriteria criteria, List<Object> params) {
        StringBuilder where = new StringBuilder("WHERE 1 = 1\n");
        if (criteria.status() != null) {
            where.append("  AND status = ?\n");
            params.add(criteria.status());
        }
        if (criteria.eventType() != null) {
            where.append("  AND event_type = ?\n");
            params.add(criteria.eventType());
        }
        if (criteria.conversationId() != null) {
            where.append("  AND conversation_id = ?\n");
            params.add(criteria.conversationId());
        }
        if (criteria.messageId() != null) {
            where.append("  AND message_id = ?\n");
            params.add(criteria.messageId());
        }
        if (criteria.actorUserId() != null) {
            where.append("  AND actor_user_id = ?\n");
            params.add(criteria.actorUserId());
        }
        if (criteria.createdFrom() != null) {
            where.append("  AND created_at >= ?\n");
            params.add(Timestamp.valueOf(criteria.createdFrom()));
        }
        if (criteria.createdTo() != null) {
            where.append("  AND created_at <= ?\n");
            params.add(Timestamp.valueOf(criteria.createdTo()));
        }
        return where.toString();
    }

    private AdminImOutboxEventRow mapOutboxEvent(ResultSet rs, int rowNum) throws SQLException {
        return new AdminImOutboxEventRow(
            rs.getLong("id"),
            rs.getString("event_type"),
            rs.getLong("conversation_id"),
            nullableLong(rs, "message_id"),
            nullableLong(rs, "seq_no"),
            nullableLong(rs, "actor_user_id"),
            rs.getString("recipient_user_ids_json"),
            rs.getString("payload_json"),
            rs.getString("status"),
            rs.getInt("retry_count"),
            rs.getTimestamp("next_retry_at").toLocalDateTime(),
            rs.getString("last_error"),
            nullableTimestamp(rs, "claimed_at"),
            nullableTimestamp(rs, "sent_at"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime nullableTimestamp(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
