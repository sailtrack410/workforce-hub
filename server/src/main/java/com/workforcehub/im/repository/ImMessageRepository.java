package com.workforcehub.im.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * module-im 消息读写与会话消息游标 JDBC 仓储。
 */
@Repository
public class ImMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public ImMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ImMessageRow> listMessages(Long conversationId, Long beforeSeq, int size) {
        if (beforeSeq == null) {
            return jdbcTemplate.query("""
                SELECT *
                FROM (
                    SELECT id AS message_id,
                           conversation_id,
                           seq_no,
                           sender_type,
                           sender_id,
                           message_type,
                           CAST(content_json AS CHAR) AS content_json,
                           quoted_message_id,
                           status,
                           sent_at
                    FROM im_message
                    WHERE conversation_id = ?
                      AND status <> 'DELETED'
                    ORDER BY seq_no DESC
                    LIMIT ?
                ) latest_page
                ORDER BY seq_no ASC
                """,
                MESSAGE_ROW_MAPPER,
                conversationId,
                size
            );
        }
        return jdbcTemplate.query("""
            SELECT *
            FROM (
                SELECT id AS message_id,
                       conversation_id,
                       seq_no,
                       sender_type,
                       sender_id,
                       message_type,
                       CAST(content_json AS CHAR) AS content_json,
                       quoted_message_id,
                       status,
                       sent_at
                FROM im_message
                WHERE conversation_id = ?
                  AND seq_no < ?
                  AND status <> 'DELETED'
                ORDER BY seq_no DESC
                LIMIT ?
            ) previous_page
            ORDER BY seq_no ASC
            """,
            MESSAGE_ROW_MAPPER,
            conversationId,
            beforeSeq,
            size
        );
    }

    public Optional<ImMessageRow> findMessageByClientMessageId(Long conversationId,
                                                               String senderType,
                                                               Long senderId,
                                                               String clientMessageId) {
        return jdbcTemplate.query("""
            SELECT id AS message_id,
                   conversation_id,
                   seq_no,
                   sender_type,
                   sender_id,
                   message_type,
                   CAST(content_json AS CHAR) AS content_json,
                   quoted_message_id,
                   status,
                   sent_at
            FROM im_message
            WHERE conversation_id = ?
              AND sender_type = ?
              AND sender_id = ?
              AND client_message_id = ?
            """,
            MESSAGE_ROW_MAPPER,
            conversationId,
            senderType,
            senderId,
            clientMessageId
        ).stream().findFirst();
    }

    public Optional<ImMessageRow> findMessageById(Long messageId) {
        return jdbcTemplate.query("""
            SELECT id AS message_id,
                   conversation_id,
                   seq_no,
                   sender_type,
                   sender_id,
                   message_type,
                   CAST(content_json AS CHAR) AS content_json,
                   quoted_message_id,
                   status,
                   sent_at
            FROM im_message
            WHERE id = ?
              AND status <> 'DELETED'
            """,
            MESSAGE_ROW_MAPPER,
            messageId
        ).stream().findFirst();
    }

    public int updateMessageContent(Long messageId,
                                    String contentJson,
                                    LocalDateTime editedAt) {
        return jdbcTemplate.update("""
            UPDATE im_message
            SET content_json = CAST(? AS JSON),
                status = 'EDITED',
                edited_at = ?
            WHERE id = ?
              AND status IN ('NORMAL', 'EDITED')
            """,
            contentJson,
            Timestamp.valueOf(editedAt),
            messageId
        );
    }

    public int recallMessage(Long messageId,
                             String contentJson,
                             LocalDateTime editedAt) {
        return jdbcTemplate.update("""
            UPDATE im_message
            SET content_json = CAST(? AS JSON),
                status = 'RECALLED',
                edited_at = ?
            WHERE id = ?
              AND status IN ('NORMAL', 'EDITED')
            """,
            contentJson,
            Timestamp.valueOf(editedAt),
            messageId
        );
    }

    public Long lockActiveConversationAndGetLastSeq(Long conversationId) {
        List<Long> rows = jdbcTemplate.query("""
            SELECT last_message_seq
            FROM im_conversation
            WHERE id = ?
              AND status = 'ACTIVE'
              AND is_deleted = 0
            FOR UPDATE
            """,
            (rs, rowNum) -> rs.getLong("last_message_seq"),
            conversationId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Optional<Long> findActiveConversationLastMessageSeq(Long conversationId) {
        return jdbcTemplate.query("""
            SELECT last_message_seq
            FROM im_conversation
            WHERE id = ?
              AND status = 'ACTIVE'
              AND is_deleted = 0
            """,
            (rs, rowNum) -> rs.getLong("last_message_seq"),
            conversationId
        ).stream().findFirst();
    }

    public void insertMessage(Long messageId,
                              Long conversationId,
                              Long seqNo,
                              String senderType,
                              Long senderId,
                              String messageType,
                              String clientMessageId,
                              String contentJson,
                              LocalDateTime sentAt) {
        jdbcTemplate.update("""
            INSERT INTO im_message
                (id, conversation_id, seq_no, sender_type, sender_id, message_type, client_message_id, content_json, status, sent_at)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), 'NORMAL', ?)
            """,
            messageId,
            conversationId,
            seqNo,
            senderType,
            senderId,
            messageType,
            clientMessageId,
            contentJson,
            Timestamp.valueOf(sentAt)
        );
    }

    public void updateConversationLastMessage(Long conversationId,
                                              Long lastMessageSeq,
                                              Long lastMessageId,
                                              LocalDateTime lastMessageAt,
                                              Long operatorId) {
        jdbcTemplate.update("""
            UPDATE im_conversation
            SET last_message_seq = ?,
                last_message_id = ?,
                last_message_at = ?,
                updated_by = ?
            WHERE id = ?
              AND status = 'ACTIVE'
              AND is_deleted = 0
            """,
            lastMessageSeq,
            lastMessageId,
            Timestamp.valueOf(lastMessageAt),
            operatorId,
            conversationId
        );
    }

    public void updateMemberLastReadSeq(Long conversationId, Long userId, Long lastReadSeq) {
        jdbcTemplate.update("""
            UPDATE im_conversation_member
            SET last_read_seq = GREATEST(last_read_seq, ?)
            WHERE conversation_id = ?
              AND member_type = 'USER'
              AND member_id = ?
              AND status = 'ACTIVE'
            """,
            lastReadSeq,
            conversationId,
            userId
        );
    }

    private static final RowMapper<ImMessageRow> MESSAGE_ROW_MAPPER = (rs, rowNum) -> new ImMessageRow(
        rs.getLong("message_id"),
        rs.getLong("conversation_id"),
        rs.getLong("seq_no"),
        rs.getString("sender_type"),
        nullableLong(rs, "sender_id"),
        rs.getString("message_type"),
        rs.getString("content_json"),
        nullableLong(rs, "quoted_message_id"),
        rs.getString("status"),
        rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toLocalDateTime()
    );

    private static Long nullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }
}
