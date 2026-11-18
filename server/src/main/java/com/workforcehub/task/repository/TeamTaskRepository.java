package com.workforcehub.task.repository;

import com.workforcehub.task.dto.TeamTaskResponse;
import com.workforcehub.org.dto.OrgUserSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 团队任务 JDBC 仓储。
 */
@Repository
public class TeamTaskRepository {

    private static final String TASK_SELECT = """
        SELECT t.*, creator.real_name AS creator_name, assignee.real_name AS assignee_name
        FROM team_task t
        LEFT JOIN org_user creator ON creator.id = t.creator_id AND creator.is_deleted = 0
        LEFT JOIN org_user assignee ON assignee.id = t.assignee_id AND assignee.is_deleted = 0
        """;

    private static final String ASSIGNEE_SCOPE = """
        FROM org_user creator JOIN org_user assignee
          ON (assignee.primary_dept_id = creator.primary_dept_id OR assignee.manager_user_id = creator.id)
        WHERE creator.id = ? AND creator.status = 'ACTIVE' AND creator.is_deleted = 0
          AND creator.employment_status IN ('PROBATION', 'ACTIVE', 'PENDING_OFFBOARD')
          AND assignee.id <> creator.id AND assignee.status = 'ACTIVE' AND assignee.is_deleted = 0
          AND assignee.employment_status IN ('PROBATION', 'ACTIVE', 'PENDING_OFFBOARD')
        """;

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

    public int update(TeamTaskResponse task, Long expectedAssigneeId, String expectedStatus) {
        return jdbcTemplate.update("""
            UPDATE team_task
            SET title = ?, description = ?, assignee_id = ?, status = ?, start_date = ?, end_date = ?, group_id = ?
            WHERE task_id = ? AND creator_id = ? AND assignee_id = ? AND status = ?
            """,
            task.getTitle(), task.getDescription(), task.getAssigneeId(), task.getStatus(),
            Date.valueOf(task.getStartDate()), Date.valueOf(task.getEndDate()), task.getGroupId(),
            task.getTaskId(), task.getCreatorId(), expectedAssigneeId, expectedStatus);
    }

    public int updateStatus(Long taskId, Long userId, String expectedStatus, String status) {
        // 负责人可能在读取后被重新分配，写入时必须再次校验当前关系。
        return jdbcTemplate.update("""
            UPDATE team_task SET status = ?
            WHERE task_id = ? AND assignee_id = ? AND status = ?
            """, status, taskId, userId, expectedStatus);
    }

    public int deleteById(Long taskId, Long creatorId) {
        return jdbcTemplate.update("DELETE FROM team_task WHERE task_id = ? AND creator_id = ?", taskId, creatorId);
    }

    public Optional<TeamTaskResponse> findById(Long taskId) {
        List<TeamTaskResponse> rows = jdbcTemplate.query(TASK_SELECT + " WHERE t.task_id = ?", this::mapRow, taskId);
        return rows.stream().findFirst();
    }

    public List<TeamTaskResponse> find(Long userId, Long assigneeId, String status, java.time.LocalDate start, java.time.LocalDate end, String scope) {
        StringBuilder sql = new StringBuilder(TASK_SELECT + " WHERE (t.creator_id = ? OR t.assignee_id = ?)");
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.add(userId);
        if ("ASSIGNED".equals(scope)) {
            sql.append(" AND t.assignee_id = ?");
            params.add(userId);
        } else if ("CREATED".equals(scope)) {
            sql.append(" AND t.creator_id = ?");
            params.add(userId);
        }
        if (assigneeId != null) {
            sql.append(" AND t.assignee_id = ?");
            params.add(assigneeId);
        }
        if (status != null) {
            sql.append(" AND t.status = ?");
            params.add(status);
        }
        if (start != null) {
            sql.append(" AND t.end_date >= ?");
            params.add(Date.valueOf(start));
        }
        if (end != null) {
            sql.append(" AND t.start_date <= ?");
            params.add(Date.valueOf(end));
        }
        sql.append(" ORDER BY t.created_at DESC, t.task_id DESC");
        return jdbcTemplate.query(sql.toString(), this::mapRow, params.toArray());
    }

    public List<OrgUserSummaryResponse> findAssignees(Long creatorId) {
        return jdbcTemplate.query("SELECT assignee.id, assignee.real_name, assignee.primary_dept_id "
            + ASSIGNEE_SCOPE + " ORDER BY assignee.real_name, assignee.id", (rs, rowNum) -> {
                OrgUserSummaryResponse response = new OrgUserSummaryResponse();
                response.setUserId(rs.getLong("id"));
                response.setRealName(rs.getString("real_name"));
                response.setPrimaryDeptId(rs.getObject("primary_dept_id", Long.class));
                return response;
            }, creatorId);
    }

    public boolean canAssign(Long creatorId, Long assigneeId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) " + ASSIGNEE_SCOPE
            + " AND assignee.id = ?", Integer.class, creatorId, assigneeId);
        return count != null && count > 0;
    }

    private TeamTaskResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        TeamTaskResponse response = new TeamTaskResponse();
        response.setTaskId(rs.getLong("task_id"));
        response.setTitle(rs.getString("title"));
        response.setDescription(rs.getString("description"));
        response.setAssigneeId(rs.getLong("assignee_id"));
        response.setCreatorId(rs.getLong("creator_id"));
        response.setCreatorName(rs.getString("creator_name"));
        response.setAssigneeName(rs.getString("assignee_name"));
        response.setStatus(rs.getString("status"));
        Date startDate = rs.getDate("start_date");
        Date endDate = rs.getDate("end_date");
        java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
        java.sql.Timestamp updatedAt = rs.getTimestamp("updated_at");
        response.setStartDate(startDate == null ? null : startDate.toLocalDate());
        response.setEndDate(endDate == null ? null : endDate.toLocalDate());
        response.setGroupId(rs.getString("group_id"));
        response.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        response.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
        return response;
    }
}
