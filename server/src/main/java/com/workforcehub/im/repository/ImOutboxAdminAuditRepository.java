package com.workforcehub.im.repository;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workforcehub.im.support.ImOutboxAdminAuditActions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * IM outbox 后台操作审计仓储。
 */
@Repository
public class ImOutboxAdminAuditRepository {

    private final JdbcTemplate jdbcTemplate;

    public ImOutboxAdminAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countOutboxAudits(AdminImOutboxAuditQueryCriteria criteria) {
        List<Object> params = new ArrayList<>();
        String sql = """
            SELECT COUNT(*)
            FROM sys_audit_log
            """ + outboxAuditWhereSql(criteria, params);
        Long count = jdbcTemplate.queryForObject(sql, Long.class, params.toArray());
        return count == null ? 0L : count;
    }

    public List<AdminImOutboxAuditRow> pageOutboxAudits(AdminImOutboxAuditQueryCriteria criteria,
                                                        long offset,
                                                        long limit) {
        List<Object> params = new ArrayList<>();
        String sql = """
            SELECT id,
                   operator_type,
                   operator_id,
                   action_type,
                   target_type,
                   target_id,
                   result,
                   CAST(detail_json AS CHAR) AS detail_json,
                   occurred_at
            FROM sys_audit_log
            """ + outboxAuditWhereSql(criteria, params)
            + """
            ORDER BY occurred_at DESC, id DESC
            LIMIT ? OFFSET ?
            """;
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, this::mapOutboxAudit, params.toArray());
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
            IdWorker.getId(),
            operatorType,
            operatorId,
            actionType,
            targetType,
            targetId,
            result,
            detailJson
        );
    }

    private String outboxAuditWhereSql(AdminImOutboxAuditQueryCriteria criteria, List<Object> params) {
        StringBuilder where = new StringBuilder("""
            WHERE operator_type = 'ADMIN'
              AND action_type IN (?, ?, ?)
            """);
        params.add(ImOutboxAdminAuditActions.RETRY_REQUESTED);
        params.add(ImOutboxAdminAuditActions.BATCH_RETRY_REQUESTED);
        params.add(ImOutboxAdminAuditActions.DISPATCH_TRIGGERED);
        if (criteria.actionType() != null) {
            where.append("  AND action_type = ?\n");
            params.add(criteria.actionType());
        }
        if (criteria.eventId() != null) {
            where.append("""
                  AND (
                        target_id = ?
                     OR JSON_CONTAINS(JSON_EXTRACT(detail_json, '$.attemptedEventIds'), CAST(? AS JSON))
                     OR JSON_CONTAINS(JSON_EXTRACT(detail_json, '$.dispatchedEventIds'), CAST(? AS JSON))
                     OR JSON_CONTAINS(JSON_EXTRACT(detail_json, '$.failedEventIds'), CAST(? AS JSON))
                     OR JSON_CONTAINS(JSON_EXTRACT(detail_json, '$.requestedEventIds'), CAST(? AS JSON))
                     OR JSON_CONTAINS(JSON_EXTRACT(detail_json, '$.succeededEventIds'), CAST(? AS JSON))
                     OR JSON_CONTAINS(JSON_EXTRACT(detail_json, '$.skippedEventIds'), CAST(? AS JSON))
                  )
                """);
            String eventIdJson = String.valueOf(criteria.eventId());
            params.add(criteria.eventId());
            params.add(eventIdJson);
            params.add(eventIdJson);
            params.add(eventIdJson);
            params.add(eventIdJson);
            params.add(eventIdJson);
            params.add(eventIdJson);
        }
        if (criteria.operatorId() != null) {
            where.append("  AND operator_id = ?\n");
            params.add(criteria.operatorId());
        }
        if (criteria.occurredFrom() != null) {
            where.append("  AND occurred_at >= ?\n");
            params.add(Timestamp.valueOf(criteria.occurredFrom()));
        }
        if (criteria.occurredTo() != null) {
            where.append("  AND occurred_at <= ?\n");
            params.add(Timestamp.valueOf(criteria.occurredTo()));
        }
        return where.toString();
    }

    private AdminImOutboxAuditRow mapOutboxAudit(ResultSet rs, int rowNum) throws SQLException {
        return new AdminImOutboxAuditRow(
            rs.getLong("id"),
            rs.getString("operator_type"),
            nullableLong(rs, "operator_id"),
            rs.getString("action_type"),
            rs.getString("target_type"),
            nullableLong(rs, "target_id"),
            rs.getString("result"),
            rs.getString("detail_json"),
            rs.getTimestamp("occurred_at").toLocalDateTime()
        );
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
