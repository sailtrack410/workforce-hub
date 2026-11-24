package com.workforcehub.personal.repository;

import com.workforcehub.personal.dto.PersonalTodoResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 个人待办 JDBC 仓储。
 */
@Repository
public class PersonalTodoRepository {

    private final JdbcTemplate jdbcTemplate;

    public PersonalTodoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(PersonalTodoResponse todo, String clientRequestId) {
        jdbcTemplate.update("""
            INSERT INTO personal_todo (todo_id, owner_id, title, category, status, start_at, due_at, note, all_day, reminder_minutes, reminder_at, client_request_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            todo.getTodoId(), todo.getOwnerId(), todo.getTitle(), todo.getCategory(),
            todo.getStatus(), todo.getStartAt() == null ? null : java.sql.Timestamp.valueOf(todo.getStartAt()),
            todo.getDueAt() == null ? null : java.sql.Timestamp.valueOf(todo.getDueAt()), todo.getNote(),
            todo.isAllDay(), todo.getReminderMinutes(), todo.getReminderAt(), clientRequestId);
    }

    public Optional<PersonalTodoResponse> findByClientRequest(Long ownerId, String clientRequestId) {
        if (clientRequestId == null) return Optional.empty();
        return jdbcTemplate.query("SELECT todo_id FROM personal_todo WHERE owner_id=? AND client_request_id=?", (rs, row) -> rs.getLong(1), ownerId, clientRequestId)
            .stream().findFirst().flatMap(id -> findByIdAndOwner(id, ownerId));
    }

    public int update(PersonalTodoResponse todo) {
        return jdbcTemplate.update("""
            UPDATE personal_todo
            SET title = ?, category = ?, start_at = ?, due_at = ?, note = ?, all_day = ?, reminder_minutes = ?,
                reminded_at = CASE WHEN reminder_at = ? THEN reminded_at ELSE NULL END, reminder_at = ?, version=version+1
            WHERE todo_id = ? AND owner_id = ? AND version=?
            """,
            todo.getTitle(), todo.getCategory(),
            todo.getStartAt() == null ? null : java.sql.Timestamp.valueOf(todo.getStartAt()),
            todo.getDueAt() == null ? null : java.sql.Timestamp.valueOf(todo.getDueAt()),
            todo.getNote(), todo.isAllDay(), todo.getReminderMinutes(), todo.getReminderAt(), todo.getReminderAt(), todo.getTodoId(), todo.getOwnerId(), todo.getVersion());
    }

    public int updateStatus(Long todoId, Long ownerId, String status, java.time.LocalDateTime completedAt, Integer version) {
        return jdbcTemplate.update("UPDATE personal_todo SET status = ?, completed_at = ?,version=version+1 WHERE todo_id = ? AND owner_id = ? AND version=?",
            status, completedAt == null ? null : java.sql.Timestamp.valueOf(completedAt), todoId, ownerId, version);
    }

    public int deleteById(Long todoId, Long ownerId, Integer version) {
        return jdbcTemplate.update("DELETE FROM personal_todo WHERE todo_id = ? AND owner_id = ? AND version=?", todoId, ownerId, version);
    }

    public Optional<PersonalTodoResponse> findByIdAndOwner(Long todoId, Long ownerId) {
        List<PersonalTodoResponse> rows = jdbcTemplate.query("""
            SELECT todo_id, owner_id, title, category, status, start_at, due_at, note, created_at, completed_at, all_day, reminder_minutes, reminder_at, version
            FROM personal_todo WHERE todo_id = ? AND owner_id = ?
            """,
            (rs, rowNum) -> mapRow(rs.getLong("todo_id"), rs.getLong("owner_id"), rs.getString("title"),
                rs.getString("category"), rs.getString("status"), rs.getTimestamp("start_at"), rs.getTimestamp("due_at"),
                rs.getString("note"), rs.getTimestamp("created_at"), rs.getTimestamp("completed_at"), rs.getBoolean("all_day"),
                rs.getObject("reminder_minutes", Integer.class), rs.getTimestamp("reminder_at"), rs.getInt("version")),
            todoId, ownerId);
        return rows.stream().findFirst();
    }

    public List<PersonalTodoResponse> find(Long ownerId, String category, String status,
                                           java.time.LocalDateTime start, java.time.LocalDateTime end) {
        StringBuilder sql = new StringBuilder("""
            SELECT todo_id, owner_id, title, category, status, start_at, due_at, note, created_at, completed_at, all_day, reminder_minutes, reminder_at, version
            FROM personal_todo WHERE owner_id = ?
            """);
        List<Object> params = new ArrayList<>();
        params.add(ownerId);
        if (category != null) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        if (start != null) {
            sql.append(" AND COALESCE(due_at, start_at) >= ?");
            params.add(java.sql.Timestamp.valueOf(start));
        }
        if (end != null) {
            sql.append(" AND COALESCE(start_at, due_at) <= ?");
            params.add(java.sql.Timestamp.valueOf(end));
        }
        sql.append(" ORDER BY created_at DESC");
        return jdbcTemplate.query(sql.toString(),
            (rs, rowNum) -> mapRow(rs.getLong("todo_id"), rs.getLong("owner_id"), rs.getString("title"),
                rs.getString("category"), rs.getString("status"), rs.getTimestamp("start_at"), rs.getTimestamp("due_at"),
                rs.getString("note"), rs.getTimestamp("created_at"), rs.getTimestamp("completed_at"), rs.getBoolean("all_day"),
                rs.getObject("reminder_minutes", Integer.class), rs.getTimestamp("reminder_at"), rs.getInt("version")),
            params.toArray());
    }

    private static PersonalTodoResponse mapRow(Long todoId, Long ownerId, String title, String category,
                                               String status, java.sql.Timestamp startAt, java.sql.Timestamp dueAt, String note,
                                               java.sql.Timestamp createdAt, java.sql.Timestamp completedAt, boolean allDay,
                                               Integer reminderMinutes, java.sql.Timestamp reminderAt, int version) {
        PersonalTodoResponse response = new PersonalTodoResponse();
        response.setTodoId(todoId);
        response.setVersion(version);
        response.setOwnerId(ownerId);
        response.setTitle(title);
        response.setCategory(category);
        response.setStatus(status);
        response.setStartAt(startAt == null ? null : startAt.toLocalDateTime());
        response.setDueAt(dueAt == null ? null : dueAt.toLocalDateTime());
        response.setAllDay(allDay);
        response.setReminderMinutes(reminderMinutes);
        response.setReminderAt(reminderAt == null ? null : reminderAt.toLocalDateTime());
        response.setNote(note);
        response.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        response.setCompletedAt(completedAt == null ? null : completedAt.toLocalDateTime());
        return response;
    }
}
