package com.workforcehub.task.repository;

import com.workforcehub.task.dto.TeamTaskResponse;
import com.workforcehub.task.dto.TeamTaskListResponse;
import com.workforcehub.task.dto.TeamTaskSubtaskResponse;
import com.workforcehub.task.dto.TeamTaskActivityResponse;
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

    public void lockTask(Long taskId) {
        jdbcTemplate.queryForList("SELECT task_id FROM team_task WHERE task_id = ? FOR UPDATE", taskId);
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

    public List<TeamTaskListResponse> lists(Long userId) {
        return jdbcTemplate.query("""
            SELECT l.*, (SELECT COUNT(*) FROM team_task t WHERE t.group_id = CONCAT('', l.list_id)
                AND (t.creator_id = ? OR t.assignee_id = ?)) AS task_count
            FROM team_task_list l WHERE l.owner_id = ? OR EXISTS (SELECT 1 FROM team_task t
                WHERE t.group_id = CONCAT('', l.list_id) AND (t.creator_id = ? OR t.assignee_id = ?))
            ORDER BY l.created_at, l.list_id
            """, (rs, n) -> new TeamTaskListResponse(rs.getLong("list_id"), rs.getString("name"),
                rs.getLong("owner_id"), rs.getLong("task_count"), userId.equals(rs.getLong("owner_id"))),
            userId, userId, userId, userId, userId);
    }

    public void fillSummaries(List<TeamTaskResponse> tasks, Long userId) {
        if (tasks.isEmpty()) return;
        var byId = tasks.stream().collect(java.util.stream.Collectors.toMap(TeamTaskResponse::getTaskId, task -> task));
        var names = lists(userId).stream().collect(java.util.stream.Collectors.toMap(list -> String.valueOf(list.listId()), TeamTaskListResponse::name));
        tasks.forEach(task -> { task.setGroupName(names.get(task.getGroupId())); task.setSubtasks(new ArrayList<>()); });
        String placeholders = String.join(",", java.util.Collections.nCopies(tasks.size(), "?"));
        Object[] taskIds = tasks.stream().map(TeamTaskResponse::getTaskId).toArray();
        jdbcTemplate.query("SELECT * FROM team_task_subtask WHERE task_id IN (" + placeholders + ") ORDER BY created_at, subtask_id",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> byId.get(rs.getLong("task_id")).getSubtasks()
                .add(new TeamTaskSubtaskResponse(rs.getLong("subtask_id"), rs.getString("title"), rs.getBoolean("completed"))), taskIds);
        var watchArgs = new ArrayList<Object>(); watchArgs.add(userId); watchArgs.addAll(byId.keySet());
        jdbcTemplate.query("SELECT task_id FROM team_task_watch WHERE user_id = ? AND task_id IN (" + placeholders + ")",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> byId.get(rs.getLong("task_id")).setWatching(true), watchArgs.toArray());
    }

    public Optional<TeamTaskListResponse> listById(Long listId, Long userId) {
        return lists(userId).stream().filter(list -> list.listId().equals(listId)).findFirst();
    }

    public Optional<Long> listOwner(String groupId) {
        return jdbcTemplate.query("SELECT owner_id FROM team_task_list WHERE CONCAT('', list_id) = ?",
            (rs, n) -> rs.getLong("owner_id"), groupId).stream().findFirst();
    }

    public String listName(String groupId) {
        return jdbcTemplate.query("SELECT name FROM team_task_list WHERE CONCAT('', list_id) = ?",
            (rs, n) -> rs.getString("name"), groupId).stream().findFirst().orElse(null);
    }

    public void saveList(Long listId, String name, Long ownerId, boolean create) {
        if (create) jdbcTemplate.update("INSERT INTO team_task_list (list_id, name, owner_id) VALUES (?, ?, ?)", listId, name, ownerId);
        else jdbcTemplate.update("UPDATE team_task_list SET name = ? WHERE list_id = ? AND owner_id = ?", name, listId, ownerId);
    }

    public boolean deleteEmptyList(Long listId, Long ownerId) {
        return jdbcTemplate.update("""
            DELETE FROM team_task_list WHERE list_id = ? AND owner_id = ?
              AND NOT EXISTS (SELECT 1 FROM team_task WHERE group_id = ?)
            """, listId, ownerId, String.valueOf(listId)) > 0;
    }

    public List<TeamTaskSubtaskResponse> subtasks(Long taskId) {
        return jdbcTemplate.query("SELECT * FROM team_task_subtask WHERE task_id = ? ORDER BY created_at, subtask_id",
            (rs, n) -> new TeamTaskSubtaskResponse(rs.getLong("subtask_id"), rs.getString("title"), rs.getBoolean("completed")), taskId);
    }

    public void addSubtask(Long subtaskId, Long taskId, String title) {
        jdbcTemplate.update("INSERT INTO team_task_subtask (subtask_id, task_id, title) VALUES (?, ?, ?)", subtaskId, taskId, title);
    }

    public int updateSubtask(Long taskId, Long subtaskId, String title, boolean completed) {
        return jdbcTemplate.update("UPDATE team_task_subtask SET title = ?, completed = ? WHERE task_id = ? AND subtask_id = ?",
            title, completed, taskId, subtaskId);
    }

    public int deleteSubtask(Long taskId, Long subtaskId) {
        return jdbcTemplate.update("DELETE FROM team_task_subtask WHERE task_id = ? AND subtask_id = ?", taskId, subtaskId);
    }

    public boolean watching(Long taskId, Long userId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM team_task_watch WHERE task_id = ? AND user_id = ?", Integer.class, taskId, userId) > 0;
    }

    public List<String> watcherNames(Long taskId) {
        return jdbcTemplate.query("""
            SELECT u.real_name FROM team_task_watch w JOIN team_task t ON t.task_id = w.task_id
            JOIN org_user u ON u.id = w.user_id AND u.is_deleted = 0
            WHERE w.task_id = ? AND (w.user_id = t.creator_id OR w.user_id = t.assignee_id)
            ORDER BY w.created_at, w.user_id
            """, (rs, n) -> rs.getString("real_name"), taskId);
    }

    public void watch(Long taskId, Long userId, boolean watching) {
        if (!watching) jdbcTemplate.update("DELETE FROM team_task_watch WHERE task_id = ? AND user_id = ?", taskId, userId);
        else jdbcTemplate.update("""
            INSERT INTO team_task_watch (task_id, user_id) VALUES (?, ?)
            ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
            """, taskId, userId);
    }

    public void activity(Long taskId, Long actorId, String kind, String content) {
        jdbcTemplate.update("INSERT INTO team_task_activity (activity_id, task_id, actor_id, kind, content) VALUES (?, ?, ?, ?, ?)",
            com.workforcehub.common.util.IdGenerator.nextId(), taskId, actorId, kind, content);
    }

    public List<TeamTaskActivityResponse> activities(Long taskId) {
        return jdbcTemplate.query("""
            SELECT a.*, u.real_name FROM team_task_activity a
            LEFT JOIN org_user u ON u.id = a.actor_id AND u.is_deleted = 0
            WHERE a.task_id = ? ORDER BY a.created_at, a.activity_id
            """, (rs, n) -> new TeamTaskActivityResponse(rs.getLong("activity_id"), rs.getLong("actor_id"),
                rs.getString("real_name"), rs.getString("kind"), rs.getString("content"), rs.getTimestamp("created_at").toLocalDateTime()), taskId);
    }

    public void deleteCollaboration(Long taskId) {
        jdbcTemplate.update("DELETE FROM team_task_subtask WHERE task_id = ?", taskId);
        jdbcTemplate.update("DELETE FROM team_task_watch WHERE task_id = ?", taskId);
        jdbcTemplate.update("DELETE FROM team_task_activity WHERE task_id = ?", taskId);
        jdbcTemplate.update("DELETE FROM team_task_attachment WHERE task_id = ?", taskId);
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
