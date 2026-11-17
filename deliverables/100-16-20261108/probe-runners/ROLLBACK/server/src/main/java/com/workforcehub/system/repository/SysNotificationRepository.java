package com.workforcehub.system.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 员工站内通知仓库（接口需求表 #10，表 sys_notification）。
 */
@Repository
public class SysNotificationRepository {

    private final JdbcTemplate jdbcTemplate;

    public SysNotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 分页查我的通知(时间倒序) */
    public List<Map<String, Object>> pageMyNotifications(Long userId, int pageNo, int pageSize) {
        int offset = (Math.max(pageNo, 1) - 1) * Math.max(pageSize, 1);
        return jdbcTemplate.queryForList("""
            SELECT id, type, title, content, link_type, link_id, read_flag, created_at
            FROM sys_notification
            WHERE user_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """, userId, pageSize, offset);
    }

    /** 我的未读数 */
    public long countUnread(Long userId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_notification WHERE user_id = ? AND read_flag = 0",
            Long.class, userId);
        return count == null ? 0 : count;
    }

    /** 标记单条已读 */
    public int markRead(Long userId, Long notificationId) {
        return jdbcTemplate.update("""
            UPDATE sys_notification
            SET read_flag = 1, read_at = NOW()
            WHERE id = ? AND user_id = ? AND read_flag = 0
            """, notificationId, userId);
    }

    /** 全部已读 */
    public int markAllRead(Long userId) {
        return jdbcTemplate.update("""
            UPDATE sys_notification
            SET read_flag = 1, read_at = NOW()
            WHERE user_id = ? AND read_flag = 0
            """, userId);
    }
    /** 写入通知(供业务场景调用) */
    public void insert(Long id, Long userId, String type, String title, String content, String linkType, String linkId) {
        jdbcTemplate.update("""
            INSERT INTO sys_notification (id, user_id, type, title, content, link_type, link_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, id, userId, type, title, content, linkType, linkId);
    }

}
