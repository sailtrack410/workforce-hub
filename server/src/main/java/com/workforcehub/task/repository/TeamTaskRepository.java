package com.workforcehub.task.repository;

import com.workforcehub.task.dto.TeamTaskResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 团队任务 JDBC 仓储。
 */
@Repository
public class TeamTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    public TeamTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(TeamTaskResponse task) {
        jdbcTemplate.update("""
            INSERT INTO team_task (task_id, title, description, assignee_id, creator_id, status, start_date, end_date, group_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            task.getTaskId(), task.getTitle(), task.getDescription(), task.getAssigneeId(),
            task.getCreatorId(), task.getStatus(),
            Date.valueOf(task.getStartDate()), Date.valueOf(task.getEndDate()), task.getGroupId());
    }

    public void update(TeamTaskResponse task) {
        jdbcTemplate.update("""
            UPDATE team_task
            SET title = ?, description = ?, assignee_id = ?, status = ?, start_date = ?, end_date = ?, group_id = ?
            WHERE task_id = ?
            """,
            task.getTitle(), task.getDescription(), task.getAssigneeId(), task.getStatus(),
            Date.valueOf(task.getStartDate()), Date.valueOf(task.getEndDate()), task.getGroupId(),
            task.getTaskId());
    }

    public void updateStatus(Long taskId, String status) {
        jdbcTemplate.update("UPDATE team_task SET status = ? WHERE task_id = ?", status, taskId);
    }

    public void deleteById(Long taskId) {
        jdbcTemplate.update("DELETE FROM team_task WHERE task_id = ?", taskId);
    }

    public Optional<TeamTaskResponse> findById(Long taskId) {
        List<TeamTaskResponse> rows = jdbcTemplate.query("""
            SELECT task_id, title, description, assignee_id, creator_id, status, start_date, end_date, group_id, created_at, updated_at
            FROM team_task WHERE task_id = ?
            """,
            (rs, rowNum) -> mapRow(rs.getLong("task_id"), rs.getString("title"), rs.getString("description"),
                rs.getLong("assignee_id"), rs.getLong("creator_id"), rs.getString("status"),
                rs.getDate("start_date"), rs.getDate("end_date"), rs.getString("group_id"),
                rs.getTimestamp("created_at"), rs.getTimestamp("updated_at")),
            taskId);
        return rows.stream().findFirst();
    }

    public List<TeamTaskResponse> find(Long assigneeId, String status, java.time.LocalDate start, java.time.LocalDate end) {
        StringBuilder sql = new StringBuilder("""
            SELECT task_id, title, description, assignee_id, creator_id, status, start_date, end_date, group_id, created_at, updated_at
            FROM team_task WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();
        if (assigneeId != null) {
            sql.append(" AND assignee_id = ?");
            params.add(assigneeId);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        if (start != null) {
            sql.append(" AND start_date <= ?");
            params.add(Date.valueOf(start));
        }
        if (end != null) {
            sql.append(" AND end_date >= ?");
            params.add(Date.valueOf(end));
        }
        sql.append(" ORDER BY created_at DESC");
        return jdbcTemplate.query(sql.toString(),
            (rs, rowNum) -> mapRow(rs.getLong("task_id"), rs.getString("title"), rs.getString("description"),
                rs.getLong("assignee_id"), rs.getLong("creator_id"), rs.getString("status"),
                rs.getDate("start_date"), rs.getDate("end_date"), rs.getString("group_id"),
                rs.getTimestamp("created_at"), rs.getTimestamp("updated_at")),
            params.toArray());
    }

    private static TeamTaskResponse mapRow(Long taskId, String title, String description,
                                           Long assigneeId, Long creatorId, String status,
                                           Date startDate, Date endDate, String groupId,
                                           java.sql.Timestamp createdAt, java.sql.Timestamp updatedAt) {
        TeamTaskResponse response = new TeamTaskResponse();
        response.setTaskId(taskId);
        response.setTitle(title);
        response.setDescription(description);
        response.setAssigneeId(assigneeId);
        response.setCreatorId(creatorId);
        response.setStatus(status);
        response.setStartDate(startDate == null ? null : startDate.toLocalDate());
        response.setEndDate(endDate == null ? null : endDate.toLocalDate());
        response.setGroupId(groupId);
        response.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        response.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
        return response;
    }
}
