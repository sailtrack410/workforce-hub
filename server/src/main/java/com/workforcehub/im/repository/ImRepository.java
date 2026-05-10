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
import java.util.Set;

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

    public Optional<ImConversationSummaryRow> findConversationSummaryForUser(Long conversationId, Long userId) {
        return jdbcTemplate.query(conversationSummarySelectSql() + """
            WHERE m.member_type = 'USER'
              AND m.member_id = ?
              AND m.status = 'ACTIVE'
              AND c.id = ?
              AND c.status = 'ACTIVE'
              AND c.is_deleted = 0
            """,
            this::mapConversationSummary,
            userId,
            conversationId
        ).stream().findFirst();
    }

    public Optional<ImUserRelationRow> findActiveOrgUser(Long userId) {
        return jdbcTemplate.query("""
            SELECT id AS user_id,
                   real_name,
                   manager_user_id,
                   employment_status,
                   status
            FROM org_user
            WHERE id = ?
              AND status = 'ACTIVE'
              AND employment_status IN ('PROBATION', 'ACTIVE', 'PENDING_OFFBOARD')
              AND is_deleted = 0
            """,
            (rs, rowNum) -> new ImUserRelationRow(
                rs.getLong("user_id"),
                rs.getString("real_name"),
                nullableLong(rs, "manager_user_id"),
                rs.getString("employment_status"),
                rs.getString("status")
            ),
            userId
        ).stream().findFirst();
    }

    public Optional<Long> findActiveConversationIdByKey(String conversationKey) {
        return jdbcTemplate.query("""
            SELECT id
            FROM im_conversation
            WHERE conversation_key = ?
              AND status = 'ACTIVE'
              AND is_deleted = 0
            """,
            (rs, rowNum) -> rs.getLong("id"),
            conversationKey
        ).stream().findFirst();
    }

    public long countActiveOrgUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", userIds.stream().map(ignored -> "?").toList());
        Object[] args = userIds.toArray();
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM org_user
            WHERE id IN (%s)
              AND status = 'ACTIVE'
              AND employment_status IN ('PROBATION', 'ACTIVE', 'PENDING_OFFBOARD')
              AND is_deleted = 0
            """.formatted(placeholders), Long.class, args);
        return count == null ? 0 : count;
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
                              String contentJson,
                              LocalDateTime sentAt) {
        jdbcTemplate.update("""
            INSERT INTO im_message
                (id, conversation_id, seq_no, sender_type, sender_id, message_type, content_json, status, sent_at)
            VALUES
                (?, ?, ?, ?, ?, ?, CAST(? AS JSON), 'NORMAL', ?)
            """,
            messageId,
            conversationId,
            seqNo,
            senderType,
            senderId,
            messageType,
            contentJson,
            Timestamp.valueOf(sentAt)
        );
    }

    public void insertPrivateConversation(Long conversationId,
                                          String conversationKey,
                                          String title,
                                          Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO im_conversation
                (id, conversation_type, conversation_key, title, last_message_seq,
                 status, created_by, updated_by, is_deleted)
            VALUES
                (?, 'PRIVATE', ?, ?, 0, 'ACTIVE', ?, ?, 0)
            """,
            conversationId,
            conversationKey,
            title,
            operatorId,
            operatorId
        );
    }

    public void insertGroupConversation(Long conversationId, String title, Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO im_conversation
                (id, conversation_type, title, last_message_seq,
                 status, created_by, updated_by, is_deleted)
            VALUES
                (?, 'GROUP', ?, 0, 'ACTIVE', ?, ?, 0)
            """,
            conversationId,
            title,
            operatorId,
            operatorId
        );
    }

    public void insertGroupProfile(Long groupProfileId,
                                   Long conversationId,
                                   String groupType,
                                   Long ownerUserId,
                                   Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO im_group_profile
                (id, conversation_id, group_type, owner_user_id, status, created_by, updated_by)
            VALUES
                (?, ?, ?, ?, 'ACTIVE', ?, ?)
            """,
            groupProfileId,
            conversationId,
            groupType,
            ownerUserId,
            operatorId,
            operatorId
        );
    }

    public void insertUserMember(Long memberRowId,
                                 Long conversationId,
                                 Long userId,
                                 String memberRole,
                                 String joinSource) {
        jdbcTemplate.update("""
            INSERT INTO im_conversation_member
                (id, conversation_id, member_type, member_id, member_role, join_source, last_read_seq, status)
            VALUES
                (?, ?, 'USER', ?, ?, ?, 0, 'ACTIVE')
            """,
            memberRowId,
            conversationId,
            userId,
            memberRole,
            joinSource
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

    private String conversationSummarySelectSql() {
        return """
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
            """;
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
