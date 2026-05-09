package com.workforcehub.im.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * module-im 第一版 JDBC 仓储。
 */
@Repository
public class ImRepository {

    private final JdbcTemplate jdbcTemplate;

    public ImRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ImConversationSummaryRow> listActiveConversationsForUser(Long userId) {
        return jdbcTemplate.query("""
            SELECT c.id AS conversation_id,
                   c.conversation_type,
                   c.conversation_key,
                   c.title,
                   c.avatar_file_id,
                   c.bound_agent_id,
                   c.last_message_seq,
                   m.last_read_seq,
                   GREATEST(c.last_message_seq - m.last_read_seq, 0) AS unread_count,
                   c.last_message_at,
                   c.status,
                   m.pin_flag,
                   m.mute_flag,
                   m.member_role,
                   lm.id AS last_message_id,
                   lm.seq_no AS last_message_seq_no,
                   lm.sender_type AS last_message_sender_type,
                   lm.sender_id AS last_message_sender_id,
                   lm.message_type AS last_message_type,
                   CAST(lm.content_json AS CHAR) AS last_message_content_json,
                   lm.quoted_message_id AS last_message_quoted_message_id,
                   lm.status AS last_message_status,
                   lm.sent_at AS last_message_sent_at
            FROM im_conversation_member m
            JOIN im_conversation c ON c.id = m.conversation_id
            LEFT JOIN im_message lm ON lm.id = c.last_message_id
            WHERE m.member_type = 'USER'
              AND m.member_id = ?
              AND m.status = 'ACTIVE'
              AND c.status = 'ACTIVE'
              AND c.is_deleted = 0
            ORDER BY m.pin_flag DESC, c.last_message_at DESC, c.id DESC
            """,
            this::mapConversationSummary,
            userId
        );
    }

    public boolean existsActiveUserMembership(Long conversationId, Long userId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM im_conversation_member m
            JOIN im_conversation c ON c.id = m.conversation_id
            WHERE c.id = ?
              AND c.status = 'ACTIVE'
              AND c.is_deleted = 0
              AND m.member_type = 'USER'
              AND m.member_id = ?
              AND m.status = 'ACTIVE'
            """, Long.class, conversationId, userId);
        return count != null && count > 0;
    }

    public boolean existsConversation(Long conversationId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM im_conversation
            WHERE id = ?
              AND is_deleted = 0
            """, Long.class, conversationId);
        return count != null && count > 0;
    }

    public List<ImMessageRow> listMessages(Long conversationId) {
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
              AND status <> 'DELETED'
            ORDER BY seq_no ASC
            """,
            MESSAGE_ROW_MAPPER,
            conversationId
        );
    }

    private ImConversationSummaryRow mapConversationSummary(ResultSet rs, int rowNum) throws SQLException {
        ImMessageRow lastMessage = null;
        Long lastMessageId = nullableLong(rs, "last_message_id");
        if (lastMessageId != null) {
            lastMessage = new ImMessageRow(
                lastMessageId,
                rs.getLong("conversation_id"),
                nullableLong(rs, "last_message_seq_no"),
                rs.getString("last_message_sender_type"),
                nullableLong(rs, "last_message_sender_id"),
                rs.getString("last_message_type"),
                rs.getString("last_message_content_json"),
                nullableLong(rs, "last_message_quoted_message_id"),
                rs.getString("last_message_status"),
                rs.getTimestamp("last_message_sent_at") == null
                    ? null
                    : rs.getTimestamp("last_message_sent_at").toLocalDateTime()
            );
        }
        return new ImConversationSummaryRow(
            rs.getLong("conversation_id"),
            rs.getString("conversation_type"),
            rs.getString("conversation_key"),
            rs.getString("title"),
            nullableLong(rs, "avatar_file_id"),
            nullableLong(rs, "bound_agent_id"),
            rs.getLong("last_message_seq"),
            rs.getLong("last_read_seq"),
            rs.getLong("unread_count"),
            rs.getTimestamp("last_message_at") == null ? null : rs.getTimestamp("last_message_at").toLocalDateTime(),
            rs.getString("status"),
            rs.getInt("pin_flag") == 1,
            rs.getInt("mute_flag") == 1,
            rs.getString("member_role"),
            lastMessage
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
