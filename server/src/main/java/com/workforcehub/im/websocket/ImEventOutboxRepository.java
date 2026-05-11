package com.workforcehub.im.websocket;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * IM WebSocket 事件 outbox 仓储。
 */
@Repository
public class ImEventOutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public ImEventOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertEvent(Long id,
                            String eventType,
                            Long conversationId,
                            Long messageId,
                            Long seqNo,
                            Long actorUserId,
                            String recipientUserIdsJson,
                            String payloadJson,
                            LocalDateTime nextRetryAt) {
        jdbcTemplate.update("""
            INSERT INTO im_event_outbox
                (id, event_type, conversation_id, message_id, seq_no, actor_user_id,
                 recipient_user_ids_json, payload_json, status, retry_count, next_retry_at)
            VALUES
                (?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), 'PENDING', 0, ?)
            """,
            id,
            eventType,
            conversationId,
            messageId,
            seqNo,
            actorUserId,
            recipientUserIdsJson,
            payloadJson,
            Timestamp.valueOf(nextRetryAt)
        );
    }

    public List<Long> claimDueEventIds(int batchSize, LocalDateTime now, LocalDateTime staleSendingBefore) {
        return jdbcTemplate.query("""
            SELECT id
            FROM im_event_outbox
            WHERE (
                    status IN ('PENDING', 'FAILED')
                    AND next_retry_at <= ?
                  )
               OR (
                    status = 'SENDING'
                    AND claimed_at < ?
                  )
            ORDER BY next_retry_at ASC, id ASC
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """,
            (rs, rowNum) -> rs.getLong("id"),
            Timestamp.valueOf(now),
            Timestamp.valueOf(staleSendingBefore),
            batchSize
        );
    }

    public int markSending(Long id, LocalDateTime now) {
        return jdbcTemplate.update("""
            UPDATE im_event_outbox
            SET status = 'SENDING',
                claimed_at = ?,
                last_error = NULL
            WHERE id = ?
              AND status IN ('PENDING', 'FAILED', 'SENDING')
            """,
            Timestamp.valueOf(now),
            id
        );
    }

    public List<ImEventOutboxRow> listEventsByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", ids.stream().map(ignored -> "?").toList());
        return jdbcTemplate.query("""
            SELECT id,
                   event_type,
                   conversation_id,
                   message_id,
                   seq_no,
                   actor_user_id,
                   CAST(recipient_user_ids_json AS CHAR) AS recipient_user_ids_json,
                   CAST(payload_json AS CHAR) AS payload_json,
                   status,
                   retry_count
            FROM im_event_outbox
            WHERE id IN (%s)
            ORDER BY id ASC
            """.formatted(placeholders),
            (rs, rowNum) -> new ImEventOutboxRow(
                rs.getLong("id"),
                rs.getString("event_type"),
                rs.getLong("conversation_id"),
                nullableLong(rs, "message_id"),
                nullableLong(rs, "seq_no"),
                nullableLong(rs, "actor_user_id"),
                rs.getString("recipient_user_ids_json"),
                rs.getString("payload_json"),
                rs.getString("status"),
                rs.getInt("retry_count")
            ),
            ids.toArray()
        );
    }

    public void markSent(Long id, LocalDateTime now) {
        jdbcTemplate.update("""
            UPDATE im_event_outbox
            SET status = 'SENT',
                sent_at = ?,
                last_error = NULL
            WHERE id = ?
            """,
            Timestamp.valueOf(now),
            id
        );
    }

    public void markFailed(Long id,
                           String status,
                           int retryCount,
                           LocalDateTime nextRetryAt,
                           String lastError) {
        jdbcTemplate.update("""
            UPDATE im_event_outbox
            SET status = ?,
                retry_count = ?,
                next_retry_at = ?,
                last_error = ?
            WHERE id = ?
            """,
            status,
            retryCount,
            Timestamp.valueOf(nextRetryAt),
            safeError(lastError),
            id
        );
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String safeError(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 512 ? trimmed.substring(0, 512) : trimmed;
    }
}
