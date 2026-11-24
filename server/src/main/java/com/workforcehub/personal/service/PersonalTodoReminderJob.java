package com.workforcehub.personal.service;

import com.workforcehub.system.service.SysNotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PersonalTodoReminderJob {
    private final JdbcTemplate jdbc;
    private final SysNotificationService notifications;

    public PersonalTodoReminderJob(JdbcTemplate jdbc, SysNotificationService notifications) {
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    @Scheduled(fixedDelayString = "${workforce.personal-todo.reminder-delay-ms:30000}",
        initialDelayString = "${workforce.personal-todo.reminder-delay-ms:30000}")
    @Transactional(rollbackFor = Exception.class)
    public void deliverDueReminders() {
        var now = LocalDateTime.now();
        var ids = jdbc.queryForList("""
            SELECT todo_id FROM personal_todo WHERE status = 'PENDING' AND reminder_at <= ? AND reminded_at IS NULL
            ORDER BY reminder_at LIMIT 100
            """, Long.class, now);
        for (Long id : ids) {
            // 与通知写入处于同一事务；条件更新阻止多实例重复发送。
            if (jdbc.update("""
                UPDATE personal_todo SET reminded_at = ?
                WHERE todo_id = ? AND status = 'PENDING' AND reminder_at <= ? AND reminded_at IS NULL
                """, now, id, now) == 0) continue;
            jdbc.query("SELECT owner_id, title FROM personal_todo WHERE todo_id = ?",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> notifications.push(rs.getLong("owner_id"), "SYSTEM",
                    "待办提醒：" + rs.getString("title"), rs.getString("title"), "PERSONAL_TODO", String.valueOf(id)), id);
        }
    }
}
