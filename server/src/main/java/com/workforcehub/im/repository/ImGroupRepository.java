package com.workforcehub.im.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * module-im 群聊资料与成员治理 JDBC 仓储。
 */
@Repository
public class ImGroupRepository {

    private final JdbcTemplate jdbcTemplate;

    public ImGroupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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

    public List<ImGroupFileRow> listGroupFiles(Long conversationId) {
        return jdbcTemplate.query("""
            SELECT ar.id AS attachment_ref_id,
                   ar.file_object_id,
                   ar.owner_id AS conversation_id,
                   ar.display_name,
                   ar.uploader_type,
                   ar.uploader_id,
                   COALESCE(u.real_name, CONCAT('USER:', ar.uploader_id)) AS uploader_name,
                   ar.deleted_at,
                   ar.is_deleted AS attachment_deleted,
                   fo.is_deleted AS file_deleted,
                   fo.mime_type,
                   fo.file_size,
                   fo.content_sha256,
                   fo.scan_status,
                   ar.created_at
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            LEFT JOIN org_user u ON ar.uploader_type = 'USER'
                                AND u.id = ar.uploader_id
                                AND u.is_deleted = 0
            WHERE ar.owner_type = 'IM_GROUP_FILE'
              AND ar.owner_id = ?
              AND ar.is_deleted = 0
              AND ar.deleted_at IS NULL
              AND fo.is_deleted = 0
            ORDER BY ar.created_at DESC, ar.id DESC
            """,
            this::mapGroupFile,
            conversationId
        );
    }

    public Optional<ImGroupFileRow> findGroupFile(Long conversationId, Long attachmentRefId) {
        return jdbcTemplate.query("""
            SELECT ar.id AS attachment_ref_id,
                   ar.file_object_id,
                   ar.owner_id AS conversation_id,
                   ar.display_name,
                   ar.uploader_type,
                   ar.uploader_id,
                   COALESCE(u.real_name, CONCAT('USER:', ar.uploader_id)) AS uploader_name,
                   ar.deleted_at,
                   ar.is_deleted AS attachment_deleted,
                   fo.is_deleted AS file_deleted,
                   fo.mime_type,
                   fo.file_size,
                   fo.content_sha256,
                   fo.scan_status,
                   ar.created_at
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            LEFT JOIN org_user u ON ar.uploader_type = 'USER'
                                AND u.id = ar.uploader_id
                                AND u.is_deleted = 0
            WHERE ar.owner_type = 'IM_GROUP_FILE'
              AND ar.owner_id = ?
              AND ar.id = ?
            """,
            this::mapGroupFile,
            conversationId,
            attachmentRefId
        ).stream().findFirst();
    }

    public Optional<ImGroupFileRow> findGroupFileByAttachmentRefId(Long attachmentRefId) {
        return jdbcTemplate.query("""
            SELECT ar.id AS attachment_ref_id,
                   ar.file_object_id,
                   ar.owner_id AS conversation_id,
                   ar.display_name,
                   ar.uploader_type,
                   ar.uploader_id,
                   COALESCE(u.real_name, CONCAT('USER:', ar.uploader_id)) AS uploader_name,
                   ar.deleted_at,
                   ar.is_deleted AS attachment_deleted,
                   fo.is_deleted AS file_deleted,
                   fo.mime_type,
                   fo.file_size,
                   fo.content_sha256,
                   fo.scan_status,
                   ar.created_at
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            LEFT JOIN org_user u ON ar.uploader_type = 'USER'
                                AND u.id = ar.uploader_id
                                AND u.is_deleted = 0
            WHERE ar.owner_type = 'IM_GROUP_FILE'
              AND ar.id = ?
            """,
            this::mapGroupFile,
            attachmentRefId
        ).stream().findFirst();
    }

    public List<ImConversationMemberRow> listActiveUserMembers(Long conversationId) {
        return jdbcTemplate.query("""
            SELECT m.member_id,
                   m.member_type,
                   COALESCE(u.real_name, CONCAT('USER:', m.member_id)) AS display_name,
                   m.member_role,
                   m.status,
                   m.joined_at
            FROM im_conversation_member m
            LEFT JOIN org_user u ON m.member_type = 'USER'
                                AND u.id = m.member_id
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
                   COALESCE(u.real_name, a.agent_name, CONCAT(m.member_type, ':', m.member_id)) AS display_name,
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

    public int trashGroupFile(Long conversationId, Long attachmentRefId, Long operatorId) {
        return jdbcTemplate.update("""
            UPDATE sys_attachment_ref
            SET deleted_at = NOW(),
                deleted_by = ?,
                purge_after = DATE_ADD(NOW(), INTERVAL 30 DAY),
                updated_by = ?
            WHERE id = ?
              AND owner_type = 'IM_GROUP_FILE'
              AND owner_id = ?
              AND is_deleted = 0
              AND deleted_at IS NULL
            """,
            operatorId,
            operatorId,
            attachmentRefId,
            conversationId
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

    private ImGroupFileRow mapGroupFile(ResultSet rs, int rowNum) throws SQLException {
        return new ImGroupFileRow(
            rs.getLong("attachment_ref_id"),
            rs.getLong("file_object_id"),
            nullableLong(rs, "conversation_id"),
            rs.getString("display_name"),
            rs.getString("uploader_type"),
            nullableLong(rs, "uploader_id"),
            rs.getString("uploader_name"),
            rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toLocalDateTime(),
            rs.getInt("attachment_deleted"),
            rs.getInt("file_deleted"),
            rs.getString("mime_type"),
            rs.getLong("file_size"),
            rs.getString("content_sha256"),
            rs.getString("scan_status"),
            rs.getTimestamp("created_at").toLocalDateTime()
        );
    }

    private Long nullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }
}
