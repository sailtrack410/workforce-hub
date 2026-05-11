package com.workforcehub.im.repository;

import org.springframework.dao.DuplicateKeyException;
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

    public List<ImConversationSummaryRow> listActiveConversationsForUser(Long userId,
                                                                         LocalDateTime beforeLastMessageAt,
                                                                         Long beforeConversationId,
                                                                         int size) {
        if (beforeLastMessageAt != null && beforeConversationId != null) {
            return jdbcTemplate.query(conversationSummarySelectSql() + """
                WHERE m.member_type = 'USER'
                  AND m.member_id = ?
                  AND m.status = 'ACTIVE'
                  AND c.status = 'ACTIVE'
                  AND c.is_deleted = 0
                  AND (
                      COALESCE(c.last_message_at, c.created_at) < ?
                      OR (
                          COALESCE(c.last_message_at, c.created_at) = ?
                          AND c.id < ?
                      )
                  )
                ORDER BY COALESCE(c.last_message_at, c.created_at) DESC, c.id DESC
                LIMIT ?
                """,
                this::mapConversationSummary,
                userId,
                Timestamp.valueOf(beforeLastMessageAt),
                Timestamp.valueOf(beforeLastMessageAt),
                beforeConversationId,
                size
            );
        }
        return jdbcTemplate.query(conversationSummarySelectSql() + """
            WHERE m.member_type = 'USER'
              AND m.member_id = ?
              AND m.status = 'ACTIVE'
              AND c.status = 'ACTIVE'
              AND c.is_deleted = 0
            ORDER BY COALESCE(c.last_message_at, c.created_at) DESC, c.id DESC
            LIMIT ?
            """,
            this::mapConversationSummary,
            userId,
            size
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

    public boolean existsLoginAllowedOrgUser(Long userId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM org_user u
            JOIN auth_user_account a ON a.user_id = u.id
                                  AND a.is_deleted = 0
            WHERE u.id = ?
              AND u.status = 'ACTIVE'
              AND u.employment_status IN ('PROBATION', 'ACTIVE', 'PENDING_OFFBOARD')
              AND u.is_deleted = 0
              AND a.account_status = 'ACTIVE'
            """, Long.class, userId);
        return count != null && count > 0;
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

    public Optional<Long> findActiveConversationBoundAgentId(Long conversationId) {
        return jdbcTemplate.query("""
            SELECT bound_agent_id
            FROM im_conversation
            WHERE id = ?
              AND conversation_type = 'AGENT'
              AND status = 'ACTIVE'
              AND is_deleted = 0
            """,
            (rs, rowNum) -> nullableLong(rs, "bound_agent_id"),
            conversationId
        ).stream().findFirst();
    }

    public boolean existsActiveAgentConversation(Long conversationId, Long agentId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM im_conversation
            WHERE id = ?
              AND conversation_type = 'AGENT'
              AND bound_agent_id = ?
              AND status = 'ACTIVE'
              AND is_deleted = 0
            """, Long.class, conversationId, agentId);
        return count != null && count > 0;
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

    public boolean existsActiveGroupConversation(Long conversationId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM im_conversation
            WHERE id = ?
              AND conversation_type = 'GROUP'
              AND status = 'ACTIVE'
              AND is_deleted = 0
            """, Long.class, conversationId);
        return count != null && count > 0;
    }

    public boolean existsActiveCustomGroupConversation(Long conversationId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM im_conversation c
            JOIN im_group_profile g ON g.conversation_id = c.id
            WHERE c.id = ?
              AND c.conversation_type = 'GROUP'
              AND c.status = 'ACTIVE'
              AND c.is_deleted = 0
              AND g.group_type = 'CUSTOM'
              AND g.status = 'ACTIVE'
            """, Long.class, conversationId);
        return count != null && count > 0;
    }

    public boolean existsSharedActiveGroupMembership(Long firstUserId, Long secondUserId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM im_conversation c
            JOIN im_conversation_member first_member
              ON first_member.conversation_id = c.id
             AND first_member.member_type = 'USER'
             AND first_member.member_id = ?
             AND first_member.status = 'ACTIVE'
            JOIN im_conversation_member second_member
              ON second_member.conversation_id = c.id
             AND second_member.member_type = 'USER'
             AND second_member.member_id = ?
             AND second_member.status = 'ACTIVE'
            WHERE c.conversation_type = 'GROUP'
              AND c.status = 'ACTIVE'
              AND c.is_deleted = 0
            """,
            Long.class,
            firstUserId,
            secondUserId
        );
        return count != null && count > 0;
    }

    public Optional<String> findActiveUserMemberRole(Long conversationId, Long userId) {
        return jdbcTemplate.query("""
            SELECT member_role
            FROM im_conversation_member
            WHERE conversation_id = ?
              AND member_type = 'USER'
              AND member_id = ?
              AND status = 'ACTIVE'
            """,
            (rs, rowNum) -> rs.getString("member_role"),
            conversationId,
            userId
        ).stream().findFirst();
    }

    public List<ImConversationMemberRow> listActiveUserMembers(Long conversationId) {
        return jdbcTemplate.query("""
            SELECT m.member_id,
                   m.member_type,
                   u.real_name AS display_name,
                   m.member_role,
                   m.status,
                   m.joined_at
            FROM im_conversation_member m
            LEFT JOIN org_user u ON u.id = m.member_id
                                AND u.is_deleted = 0
            WHERE m.conversation_id = ?
              AND m.member_type = 'USER'
              AND m.status = 'ACTIVE'
            ORDER BY CASE member_role
                         WHEN 'OWNER' THEN 0
                         WHEN 'ADMIN' THEN 1
                         ELSE 2
                     END,
                     m.joined_at ASC,
                     m.id ASC
            """,
            this::mapConversationMember,
            conversationId
        );
    }

    public List<ImConversationMemberRow> listActiveMembers(Long conversationId) {
        return jdbcTemplate.query("""
            SELECT m.member_id,
                   m.member_type,
                   COALESCE(u.real_name, a.agent_name) AS display_name,
                   m.member_role,
                   m.status,
                   m.joined_at
            FROM im_conversation_member m
            LEFT JOIN org_user u ON m.member_type = 'USER'
                                AND u.id = m.member_id
                                AND u.is_deleted = 0
            LEFT JOIN agt_agent a ON m.member_type = 'AGENT'
                                 AND a.id = m.member_id
                                 AND a.is_deleted = 0
            WHERE m.conversation_id = ?
              AND m.status = 'ACTIVE'
            ORDER BY CASE m.member_type
                         WHEN 'USER' THEN 0
                         WHEN 'AGENT' THEN 1
                         ELSE 2
                     END,
                     CASE m.member_role
                         WHEN 'OWNER' THEN 0
                         WHEN 'ADMIN' THEN 1
                         ELSE 2
                     END,
                     m.joined_at ASC,
                     m.id ASC
            """,
            this::mapConversationMember,
            conversationId
        );
    }

    public List<Long> listPushableActiveUserMemberIds(Long conversationId) {
        return jdbcTemplate.query("""
            SELECT m.member_id
            FROM im_conversation_member m
            JOIN org_user u ON u.id = m.member_id
                           AND u.status = 'ACTIVE'
                           AND u.employment_status IN ('PROBATION', 'ACTIVE', 'PENDING_OFFBOARD')
                           AND u.is_deleted = 0
            JOIN auth_user_account a ON a.user_id = u.id
                                    AND a.account_status = 'ACTIVE'
                                    AND a.is_deleted = 0
            WHERE m.conversation_id = ?
              AND m.member_type = 'USER'
              AND m.status = 'ACTIVE'
            ORDER BY m.joined_at ASC, m.id ASC
            """,
            (rs, rowNum) -> rs.getLong("member_id"),
            conversationId
        );
    }

    public Optional<ImGroupProfileRow> findActiveGroupProfile(Long conversationId) {
        return jdbcTemplate.query("""
            SELECT c.id AS conversation_id,
                   c.title,
                   c.avatar_file_id,
                   g.group_type,
                   g.owner_user_id,
                   g.notice_text,
                   g.status
            FROM im_conversation c
            JOIN im_group_profile g ON g.conversation_id = c.id
            WHERE c.id = ?
              AND c.conversation_type = 'GROUP'
              AND c.status = 'ACTIVE'
              AND c.is_deleted = 0
              AND g.status = 'ACTIVE'
            """,
            (rs, rowNum) -> new ImGroupProfileRow(
                rs.getLong("conversation_id"),
                rs.getString("title"),
                nullableLong(rs, "avatar_file_id"),
                rs.getString("group_type"),
                nullableLong(rs, "owner_user_id"),
                rs.getString("notice_text"),
                rs.getString("status")
            ),
            conversationId
        ).stream().findFirst();
    }

    public void updateGroupProfile(Long conversationId,
                                   String title,
                                   String noticeText,
                                   Long operatorId) {
        jdbcTemplate.update("""
            UPDATE im_conversation
            SET title = COALESCE(?, title),
                updated_by = ?
            WHERE id = ?
              AND conversation_type = 'GROUP'
              AND status = 'ACTIVE'
              AND is_deleted = 0
            """,
            title,
            operatorId,
            conversationId
        );
        jdbcTemplate.update("""
            UPDATE im_group_profile
            SET notice_text = COALESCE(?, notice_text),
                updated_by = ?
            WHERE conversation_id = ?
              AND status = 'ACTIVE'
            """,
            noticeText,
            operatorId,
            conversationId
        );
    }

    public int markUserMemberLeft(Long conversationId, Long userId) {
        return jdbcTemplate.update("""
            UPDATE im_conversation_member
            SET status = 'LEFT',
                left_at = CURRENT_TIMESTAMP
            WHERE conversation_id = ?
              AND member_type = 'USER'
              AND member_id = ?
              AND status = 'ACTIVE'
              AND member_role <> 'OWNER'
            """,
            conversationId,
            userId
        );
    }

    public void dismissGroupConversation(Long conversationId, Long operatorId) {
        jdbcTemplate.update("""
            UPDATE im_conversation
            SET status = 'DISMISSED',
                updated_by = ?
            WHERE id = ?
              AND conversation_type = 'GROUP'
              AND status = 'ACTIVE'
              AND is_deleted = 0
            """,
            operatorId,
            conversationId
        );
        jdbcTemplate.update("""
            UPDATE im_group_profile
            SET status = 'DISMISSED',
                updated_by = ?
            WHERE conversation_id = ?
              AND status = 'ACTIVE'
            """,
            operatorId,
            conversationId
        );
        jdbcTemplate.update("""
            UPDATE im_conversation_member
            SET status = 'LEFT',
                left_at = CURRENT_TIMESTAMP
            WHERE conversation_id = ?
              AND status = 'ACTIVE'
            """,
            conversationId
        );
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

    public Long createImMessageAttachmentRef(Long messageId,
                                             ImAttachmentRow sourceAttachment,
                                             Long uploaderId,
                                             int sortOrder) {
        Long attachmentRefId = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        jdbcTemplate.update("""
            INSERT INTO sys_attachment_ref
                (id, owner_type, owner_id, file_object_id, original_file_name,
                 display_name, uploader_type, uploader_id, visibility_scope, sort_order, created_by)
            VALUES
                (?, 'IM_MESSAGE', ?, ?, ?, ?, 'USER', ?, 'CONVERSATION_MEMBER', ?, ?)
            """,
            attachmentRefId,
            messageId,
            sourceAttachment.fileObjectId(),
            sourceAttachment.displayName(),
            sourceAttachment.displayName(),
            uploaderId,
            sortOrder,
            uploaderId
        );
        jdbcTemplate.update("""
            UPDATE sys_file_object
            SET ref_count = ref_count + 1
            WHERE id = ? AND is_deleted = 0
            """, sourceAttachment.fileObjectId());
        return attachmentRefId;
    }

    public Optional<ImAttachmentRow> findAttachmentById(Long attachmentRefId) {
        return jdbcTemplate.query("""
            SELECT ar.id AS attachment_ref_id,
                   ar.file_object_id,
                   ar.owner_type,
                   ar.owner_id,
                   ar.display_name,
                   ar.uploader_type,
                   ar.uploader_id,
                   ar.deleted_at,
                   ar.is_deleted AS attachment_deleted,
                   fo.is_deleted AS file_deleted,
                   fo.mime_type,
                   fo.file_size,
                   fo.content_sha256,
                   fo.scan_status
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.id = ?
            """,
            this::mapAttachment,
            attachmentRefId
        ).stream().findFirst();
    }

    public List<ImAttachmentRow> listMessageAttachments(Long messageId) {
        return jdbcTemplate.query("""
            SELECT ar.id AS attachment_ref_id,
                   ar.file_object_id,
                   ar.owner_type,
                   ar.owner_id,
                   ar.display_name,
                   ar.uploader_type,
                   ar.uploader_id,
                   ar.deleted_at,
                   ar.is_deleted AS attachment_deleted,
                   fo.is_deleted AS file_deleted,
                   fo.mime_type,
                   fo.file_size,
                   fo.content_sha256,
                   fo.scan_status
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.owner_type = 'IM_MESSAGE'
              AND ar.owner_id = ?
            ORDER BY ar.sort_order ASC, ar.created_at ASC, ar.id ASC
            """,
            this::mapAttachment,
            messageId
        );
    }

    public void insertDownloadTicket(Long ticketId,
                                     String ticketNo,
                                     Long attachmentRefId,
                                     Long requesterId,
                                     String downloadUrl,
                                     String reason,
                                     LocalDateTime expireAt) {
        jdbcTemplate.update("""
            INSERT INTO sys_download_ticket
                (id, ticket_no, attachment_ref_id, requester_type, requester_id,
                 download_type, download_url, reason, expire_at, status)
            VALUES
                (?, ?, ?, 'USER', ?, 'SINGLE', ?, ?, ?, 'ACTIVE')
            """,
            ticketId,
            ticketNo,
            attachmentRefId,
            requesterId,
            downloadUrl,
            reason,
            Timestamp.valueOf(expireAt)
        );
    }

    public Optional<ImDownloadTicketRow> findDownloadTicket(String ticketNo) {
        return jdbcTemplate.query("""
            SELECT id, ticket_no, attachment_ref_id, requester_type, requester_id,
                   expire_at, status
            FROM sys_download_ticket
            WHERE ticket_no = ? AND is_deleted = 0
            LIMIT 1
            """,
            (rs, rowNum) -> new ImDownloadTicketRow(
                rs.getLong("id"),
                rs.getString("ticket_no"),
                rs.getLong("attachment_ref_id"),
                rs.getString("requester_type"),
                rs.getLong("requester_id"),
                rs.getTimestamp("expire_at").toLocalDateTime(),
                rs.getString("status")
            ),
            ticketNo
        ).stream().findFirst();
    }

    public void insertAudit(String operatorType,
                            Long operatorId,
                            String actionType,
                            String targetType,
                            Long targetId,
                            String result,
                            String detailJson) {
        jdbcTemplate.update("""
            INSERT INTO sys_audit_log
                (id, operator_type, operator_id, action_type, target_type, target_id, result, detail_json)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON))
            """,
            com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),
            operatorType,
            operatorId,
            actionType,
            targetType,
            targetId,
            result,
            detailJson
        );
    }

    private ImConversationMemberRow mapConversationMember(ResultSet rs, int rowNum) throws SQLException {
        return new ImConversationMemberRow(
            rs.getLong("member_id"),
            rs.getString("member_type"),
            rs.getString("display_name"),
            rs.getString("member_role"),
            rs.getString("status"),
            rs.getTimestamp("joined_at") == null ? null : rs.getTimestamp("joined_at").toLocalDateTime()
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

    public boolean tryInsertPrivateConversation(Long conversationId,
                                                String conversationKey,
                                                String title,
                                                Long operatorId) {
        try {
            insertPrivateConversation(conversationId, conversationKey, title, operatorId);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
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

    public void insertAgentConversation(Long conversationId,
                                        String conversationKey,
                                        String title,
                                        Long agentId,
                                        Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO im_conversation
                (id, conversation_type, conversation_key, title, bound_agent_id, last_message_seq,
                 status, created_by, updated_by, is_deleted)
            VALUES
                (?, 'AGENT', ?, ?, ?, 0, 'ACTIVE', ?, ?, 0)
            """,
            conversationId,
            conversationKey,
            title,
            agentId,
            operatorId,
            operatorId
        );
    }

    public boolean tryInsertAgentConversation(Long conversationId,
                                              String conversationKey,
                                              String title,
                                              Long agentId,
                                              Long operatorId) {
        try {
            insertAgentConversation(conversationId, conversationKey, title, agentId, operatorId);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
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

    public void insertAgentMember(Long memberRowId,
                                  Long conversationId,
                                  Long agentId,
                                  String joinSource) {
        jdbcTemplate.update("""
            INSERT INTO im_conversation_member
                (id, conversation_id, member_type, member_id, member_role, join_source, last_read_seq, status)
            VALUES
                (?, ?, 'AGENT', ?, 'MEMBER', ?, 0, 'ACTIVE')
            """,
            memberRowId,
            conversationId,
            agentId,
            joinSource
        );
    }

    public void upsertUserMember(Long memberRowId,
                                 Long conversationId,
                                 Long userId,
                                 String memberRole,
                                 String joinSource) {
        jdbcTemplate.update("""
            INSERT INTO im_conversation_member
                (id, conversation_id, member_type, member_id, member_role, join_source, last_read_seq, status)
            VALUES
                (?, ?, 'USER', ?, ?, ?, 0, 'ACTIVE')
            ON DUPLICATE KEY UPDATE
                member_role = ?,
                join_source = ?,
                joined_at = IF(left_at IS NOT NULL, CURRENT_TIMESTAMP, joined_at),
                status = 'ACTIVE',
                left_at = NULL
            """,
            memberRowId,
            conversationId,
            userId,
            memberRole,
            joinSource,
            memberRole,
            joinSource
        );
    }

    public void markUserMembersLeft(Long conversationId, Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", userIds.stream().map(ignored -> "?").toList());
        Object[] args = new Object[userIds.size() + 1];
        args[0] = conversationId;
        int index = 1;
        for (Long userId : userIds) {
            args[index++] = userId;
        }
        jdbcTemplate.update("""
            UPDATE im_conversation_member
            SET status = 'LEFT',
                left_at = CURRENT_TIMESTAMP
            WHERE conversation_id = ?
              AND member_type = 'USER'
              AND status = 'ACTIVE'
              AND member_role <> 'OWNER'
              AND member_id IN (%s)
            """.formatted(placeholders), args);
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

    public int updateMemberPreference(Long conversationId, Long userId, Boolean pin, Boolean mute) {
        List<String> assignments = new java.util.ArrayList<>();
        List<Object> args = new java.util.ArrayList<>();
        if (pin != null) {
            assignments.add("pin_flag = ?");
            args.add(Boolean.TRUE.equals(pin) ? 1 : 0);
        }
        if (mute != null) {
            assignments.add("mute_flag = ?");
            args.add(Boolean.TRUE.equals(mute) ? 1 : 0);
        }
        if (assignments.isEmpty()) {
            return 0;
        }
        args.add(conversationId);
        args.add(userId);
        return jdbcTemplate.update("""
            UPDATE im_conversation_member
            SET %s
            WHERE conversation_id = ?
              AND member_type = 'USER'
              AND member_id = ?
              AND status = 'ACTIVE'
            """.formatted(String.join(", ", assignments)), args.toArray());
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

    private ImAttachmentRow mapAttachment(ResultSet rs, int rowNum) throws SQLException {
        return new ImAttachmentRow(
            rs.getLong("attachment_ref_id"),
            rs.getLong("file_object_id"),
            rs.getString("owner_type"),
            nullableLong(rs, "owner_id"),
            rs.getString("display_name"),
            rs.getString("uploader_type"),
            nullableLong(rs, "uploader_id"),
            rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toLocalDateTime(),
            rs.getInt("attachment_deleted"),
            rs.getInt("file_deleted"),
            rs.getString("mime_type"),
            rs.getLong("file_size"),
            rs.getString("content_sha256"),
            rs.getString("scan_status")
        );
    }

    public record ImDownloadTicketRow(
        Long id,
        String ticketNo,
        Long attachmentRefId,
        String requesterType,
        Long requesterId,
        LocalDateTime expireAt,
        String status
    ) {
    }
}
