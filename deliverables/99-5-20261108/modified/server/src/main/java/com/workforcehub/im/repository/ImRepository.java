package com.workforcehub.im.repository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
            Boolean beforePin = findConversationPinForUser(beforeConversationId, userId).orElse(null);
            if (beforePin == null) {
                return List.of();
            }
            int beforePinFlag = Boolean.TRUE.equals(beforePin) ? 1 : 0;
            return jdbcTemplate.query(conversationSummarySelectSql() + """
                WHERE m.member_type = 'USER'
                  AND m.member_id = ?
                  AND m.status = 'ACTIVE'
                  AND c.status = 'ACTIVE'
                  AND c.is_deleted = 0
                  AND (
                      m.pin_flag < ?
                      OR (
                          m.pin_flag = ?
                          AND (
                              COALESCE(c.last_message_at, c.created_at) < ?
                              OR (
                                  COALESCE(c.last_message_at, c.created_at) = ?
                                  AND c.id < ?
                              )
                          )
                      )
                  )
                ORDER BY m.pin_flag DESC, COALESCE(c.last_message_at, c.created_at) DESC, c.id DESC
                LIMIT ?
                """,
                this::mapConversationSummary,
                userId,
                beforePinFlag,
                beforePinFlag,
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
            ORDER BY m.pin_flag DESC, COALESCE(c.last_message_at, c.created_at) DESC, c.id DESC
            LIMIT ?
            """,
            this::mapConversationSummary,
            userId,
            size
        );
    }

    private Optional<Boolean> findConversationPinForUser(Long conversationId, Long userId) {
        return jdbcTemplate.query("""
            SELECT pin_flag
            FROM im_conversation_member
            WHERE conversation_id = ?
              AND member_type = 'USER'
              AND member_id = ?
              AND status = 'ACTIVE'
            """,
            (rs, rowNum) -> rs.getInt("pin_flag") == 1,
            conversationId,
            userId
        ).stream().findFirst();
    }

    private String conversationSummarySelectSql() {
        return """
            SELECT c.id AS conversation_id,
                   c.conversation_type,
                   c.conversation_key,
                   c.title,
                   COALESCE(
                       CASE
                           WHEN c.conversation_type = 'PRIVATE'
                                AND private_avatar_ref.id IS NOT NULL
                                AND private_avatar_file.id IS NOT NULL
                           THEN private_user.avatar_attachment_ref_id
                       END,
                       CASE WHEN conversation_avatar_ref.id IS NULL OR conversation_avatar_file.id IS NULL THEN NULL ELSE c.avatar_file_id END
                   ) AS avatar_file_id,
                   c.bound_agent_id,
                   c.last_message_seq,
                   m.last_read_seq,
                   GREATEST(c.last_message_seq - m.last_read_seq, 0) AS unread_count,
                   c.last_message_at,
                   c.status,
                   m.pin_flag,
                   m.mute_flag,
                   m.member_role,
                   EXISTS (
                       SELECT 1
                       FROM im_message mention_message
                       WHERE mention_message.conversation_id = c.id
                         AND mention_message.seq_no > m.last_read_seq
                         AND mention_message.status IN ('NORMAL', 'EDITED')
                         AND mention_message.sender_type = 'USER'
                         AND mention_message.sender_id <> m.member_id
                         AND JSON_CONTAINS(JSON_EXTRACT(mention_message.content_json, '$.mentionUserIds'), CAST(CAST(m.member_id AS CHAR) AS JSON))
                   ) AS has_mention_me,
                   EXISTS (
                       SELECT 1
                       FROM im_message reply_message
                       JOIN im_message quoted_message ON quoted_message.id = reply_message.quoted_message_id
                       WHERE reply_message.conversation_id = c.id
                         AND reply_message.seq_no > m.last_read_seq
                         AND reply_message.status IN ('NORMAL', 'EDITED')
                         AND reply_message.sender_type = 'USER'
                         AND reply_message.sender_id <> m.member_id
                         AND quoted_message.sender_type = 'USER'
                         AND quoted_message.sender_id = m.member_id
                         AND quoted_message.conversation_id = c.id
                   ) AS has_reply_me,
                   lm.id AS last_message_id,
                   lm.seq_no AS last_message_seq_no,
                   lm.sender_type AS last_message_sender_type,
                   lm.sender_id AS last_message_sender_id,
                   lm.message_type AS last_message_type,
                   CAST(lm.content_json AS CHAR) AS last_message_content_json,
                   lm.quoted_message_id AS last_message_quoted_message_id,
                   lm.status AS last_message_status,
                   lm.sent_at AS last_message_sent_at,
                   (SELECT COUNT(*) FROM im_conversation_member cm
                     WHERE cm.conversation_id = c.id AND cm.status = 'ACTIVE') AS member_count,
                   m.save_to_contacts_flag AS save_to_contacts,
                   m.show_member_nickname_flag AS show_member_nickname
            FROM im_conversation_member m
            JOIN im_conversation c ON c.id = m.conversation_id
            LEFT JOIN im_conversation_member private_peer
                   ON c.conversation_type = 'PRIVATE'
                  AND private_peer.conversation_id = c.id
                  AND private_peer.member_type = 'USER'
                  AND private_peer.member_id <> m.member_id
                  AND private_peer.status = 'ACTIVE'
            LEFT JOIN org_user private_user
                   ON private_user.id = private_peer.member_id
                  AND private_user.is_deleted = 0
            LEFT JOIN sys_attachment_ref private_avatar_ref
                   ON private_avatar_ref.id = private_user.avatar_attachment_ref_id
                  AND private_avatar_ref.owner_type = 'USER_AVATAR'
                  AND private_avatar_ref.uploader_type = 'USER'
                  AND private_avatar_ref.uploader_id = private_user.id
                  AND private_avatar_ref.deleted_at IS NULL
                  AND private_avatar_ref.is_deleted = 0
            LEFT JOIN sys_file_object private_avatar_file
                   ON private_avatar_file.id = private_avatar_ref.file_object_id
                  AND private_avatar_file.is_deleted = 0
            LEFT JOIN sys_attachment_ref conversation_avatar_ref
                   ON conversation_avatar_ref.id = c.avatar_file_id
                  AND conversation_avatar_ref.owner_type = 'USER_AVATAR'
                  AND conversation_avatar_ref.uploader_type = 'USER'
                  AND conversation_avatar_ref.deleted_at IS NULL
                  AND conversation_avatar_ref.is_deleted = 0
            LEFT JOIN sys_file_object conversation_avatar_file
                   ON conversation_avatar_file.id = conversation_avatar_ref.file_object_id
                  AND conversation_avatar_file.is_deleted = 0
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

    public Long createImMessageAttachmentRef(Long messageId,
                                             ImAttachmentRow sourceAttachment,
                                             Long uploaderId,
                                             int sortOrder) {
        Long attachmentRefId = com.workforcehub.common.util.IdGenerator.nextId();
        jdbcTemplate.update("""
            INSERT INTO sys_attachment_ref
                (id, owner_type, owner_id, file_object_id, original_file_name,
                 display_name, uploader_type, uploader_id, visibility_scope, sort_order, created_by, updated_by)
            VALUES
                (?, 'IM_MESSAGE', ?, ?, ?, ?, 'USER', ?, 'CONVERSATION_MEMBER', ?, ?, ?)
            """,
            attachmentRefId,
            messageId,
            sourceAttachment.fileObjectId(),
            sourceAttachment.displayName(),
            sourceAttachment.displayName(),
            uploaderId,
            sortOrder,
            uploaderId,
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
            com.workforcehub.common.util.IdGenerator.nextId(),
            operatorType,
            operatorId,
            actionType,
            targetType,
            targetId,
            result,
            detailJson
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


    /** 接口需求表 #3：设置本群昵称(空串=清除) */
    public int updateGroupNickname(Long conversationId, Long userId, String nickname) {
        return jdbcTemplate.update("""
            UPDATE im_conversation_member
            SET group_nickname = ?
            WHERE conversation_id = ?
              AND member_type = 'USER'
              AND member_id = ?
              AND status = 'ACTIVE'
            """,
            nickname == null || nickname.isBlank() ? null : nickname,
            conversationId,
            userId
        );
    }

    /** 接口需求表 #4：清空当前用户视角的会话消息(推进 cleared_seq 到会话最新 seq) */
    public int clearConversationForUser(Long conversationId, Long userId) {
        return jdbcTemplate.update("""
            UPDATE im_conversation_member m
            JOIN im_conversation c ON c.id = m.conversation_id
            SET m.cleared_seq = c.last_message_seq
            WHERE m.conversation_id = ?
              AND m.member_type = 'USER'
              AND m.member_id = ?
              AND m.status = 'ACTIVE'
            """,
            conversationId,
            userId
        );
    }
        public int updateMemberPreference(Long conversationId, Long userId, Boolean pin, Boolean mute,
                                      Boolean saveToContacts, Boolean showMemberNickname) {
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
        if (saveToContacts != null) {
            assignments.add("save_to_contacts_flag = ?");
            args.add(Boolean.TRUE.equals(saveToContacts) ? 1 : 0);
        }
        if (showMemberNickname != null) {
            assignments.add("show_member_nickname_flag = ?");
            args.add(Boolean.TRUE.equals(showMemberNickname) ? 1 : 0);
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
            rs.getInt("has_mention_me") == 1,
            rs.getInt("has_reply_me") == 1,
            rs.getLong("member_count"),
            rs.getInt("save_to_contacts") == 1,
            rs.getInt("show_member_nickname") == 1,
            lastMessage
        );
    }

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
