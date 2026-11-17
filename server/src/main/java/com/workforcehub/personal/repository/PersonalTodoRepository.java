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

    public void insert(PersonalTodoResponse todo) {
        jdbcTemplate.update("""
            INSERT INTO personal_todo (todo_id, owner_id, title, category, status, due_at, note)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            todo.getTodoId(), todo.getOwnerId(), todo.getTitle(), todo.getCategory(),
            todo.getStatus(), java.sql.Timestamp.valueOf(todo.getDueAt()), todo.getNote());
    }

    public void update(PersonalTodoResponse todo) {
        jdbcTemplate.update("""
            UPDATE personal_todo
            SET title = ?, category = ?, due_at = ?, note = ?
            WHERE todo_id = ? AND owner_id = ?
            """,
            todo.getTitle(), todo.getCategory(),
            todo.getDueAt() == null ? null : java.sql.Timestamp.valueOf(todo.getDueAt()),
            todo.getNote(), todo.getTodoId(), todo.getOwnerId());
    }

    public void updateStatus(Long todoId, Long ownerId, String status, java.time.LocalDateTime completedAt) {
        jdbcTemplate.update("UPDATE personal_todo SET status = ?, completed_at = ? WHERE todo_id = ? AND owner_id = ?",
            status, completedAt == null ? null : java.sql.Timestamp.valueOf(completedAt), todoId, ownerId);
    }

    public void deleteById(Long todoId, Long ownerId) {
        jdbcTemplate.update("DELETE FROM personal_todo WHERE todo_id = ? AND owner_id = ?", todoId, ownerId);
    }

    public Optional<PersonalTodoResponse> findByIdAndOwner(Long todoId, Long ownerId) {
        List<PersonalTodoResponse> rows = jdbcTemplate.query("""
            SELECT todo_id, owner_id, title, category, status, due_at, note, created_at, completed_at
            FROM personal_todo WHERE todo_id = ? AND owner_id = ?
            """,
            (rs, rowNum) -> mapRow(rs.getLong("todo_id"), rs.getLong("owner_id"), rs.getString("title"),
                rs.getString("category"), rs.getString("status"), rs.getTimestamp("due_at"),
                rs.getString("note"), rs.getTimestamp("created_at"), rs.getTimestamp("completed_at")),
            todoId, ownerId);
        return rows.stream().findFirst();
    }

    public List<PersonalTodoResponse> find(Long ownerId, String category, String status,
                                           java.time.LocalDateTime start, java.time.LocalDateTime end) {
        StringBuilder sql = new StringBuilder("""
            SELECT todo_id, owner_id, title, category, status, due_at, note, created_at, completed_at
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
            sql.append(" AND due_at >= ?");
            params.add(java.sql.Timestamp.valueOf(start));
        }
        if (end != null) {
            sql.append(" AND due_at <= ?");
            params.add(java.sql.Timestamp.valueOf(end));
        }
        sql.append(" ORDER BY created_at DESC");
        return jdbcTemplate.query(sql.toString(),
            (rs, rowNum) -> mapRow(rs.getLong("todo_id"), rs.getLong("owner_id"), rs.getString("title"),
                rs.getString("category"), rs.getString("status"), rs.getTimestamp("due_at"),
                rs.getString("note"), rs.getTimestamp("created_at"), rs.getTimestamp("completed_at")),
            params.toArray());
    }

    private static PersonalTodoResponse mapRow(Long todoId, Long ownerId, String title, String category,
                                               String status, java.sql.Timestamp dueAt, String note,
                                               java.sql.Timestamp createdAt, java.sql.Timestamp completedAt) {
        PersonalTodoResponse response = new PersonalTodoResponse();
        response.setTodoId(todoId);
        response.setOwnerId(ownerId);
        response.setTitle(title);
        response.setCategory(category);
        response.setStatus(status);
        response.setDueAt(dueAt == null ? null : dueAt.toLocalDateTime());
        response.setNote(note);
        response.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        response.setCompletedAt(completedAt == null ? null : completedAt.toLocalDateTime());
        return response;
    }
}
