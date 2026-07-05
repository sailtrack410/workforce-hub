package com.workforcehub.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.audit.AuditLogWriter;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.workforcehub.common.util.JsonValues.write;

@Service
public class AdminAlertService {

    private static final List<AlertSource> ALERT_SOURCES = List.of(
        new AlertSource("AGENT_TASK_FAILED", "SELECT COUNT(*) FROM agt_task WHERE status = 'FAILED'"),
        new AlertSource("AGENT_TIMEOUT_SCAN_FAILED", "SELECT COUNT(*) FROM agt_task_timeout_job_run WHERE status = 'FAILED'"),
        new AlertSource("IM_OUTBOX_DEAD", "SELECT COUNT(*) FROM im_event_outbox WHERE status = 'DEAD'"),
        new AlertSource("IM_OUTBOX_STALE_SENDING", """
            SELECT COUNT(*)
            FROM im_event_outbox
            WHERE status = 'SENDING'
              AND claimed_at IS NOT NULL
              AND claimed_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE)
            """),
        new AlertSource("RAGFLOW_INGEST_FAILED", "SELECT COUNT(*) FROM kb_ingest_job WHERE status = 'FAILED' OR vector_status = 'FAILED'")
    );

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;

    public AdminAlertService(JdbcTemplate jdbcTemplate, AuditLogWriter auditLogWriter, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogWriter = auditLogWriter;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listActiveAlerts() {
        List<Map<String, Object>> alerts = new ArrayList<>();
        for (AlertSource source : ALERT_SOURCES) {
            Long count = jdbcTemplate.queryForObject(source.countSql(), Long.class);
            if (count == null || count <= 0) {
                continue;
            }
            Map<String, Object> rule = findRule(source.alertCode());
            if (rule == null) {
                continue;
            }
            rule.put("count", count);
            alerts.add(rule);
        }
        return alerts;
    }

    public List<Map<String, Object>> listChannels() {
        return jdbcTemplate.query("""
            SELECT id, channel_code, channel_name, channel_type, target_ref, enabled
            FROM ops_alert_channel
            ORDER BY channel_code ASC, id ASC
            """, (rs, rowNum) -> channelRow(rs));
    }

    @Transactional
    public Map<String, Object> createChannel(Map<String, Object> request) {
        String channelCode = requiredText(request, "channelCode");
        jdbcTemplate.update("""
            INSERT INTO ops_alert_channel
                (id, channel_code, channel_name, channel_type, target_ref, enabled)
            VALUES
                (?, ?, ?, ?, ?, ?)
            """,
            IdGenerator.nextId(),
            channelCode,
            requiredText(request, "channelName"),
            requiredText(request, "channelType"),
            optionalText(request, "targetRef"),
            booleanValue(request.get("enabled")) ? 1 : 0
        );
        return getChannel(channelCode);
    }

    @Transactional
    public Map<String, Object> updateRule(String alertCode, Map<String, Object> request) {
        int updated = jdbcTemplate.update("""
            UPDATE ops_alert_rule
            SET enabled = ?,
                notify_enabled = ?,
                channel_id = ?,
                cooldown_minutes = ?
            WHERE alert_code = ?
            """,
            booleanValue(request.get("enabled")) ? 1 : 0,
            booleanValue(request.get("notifyEnabled")) ? 1 : 0,
            longValue(request.get("channelId")),
            intValue(request.get("cooldownMinutes")),
            alertCode
        );
        if (updated == 0) {
            throw new BizException(404, "告警规则不存在");
        }
        return findRule(alertCode);
    }

    @Transactional
    public Map<String, Object> createResolution(String alertCode, Map<String, Object> request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        String actionType = requiredText(request, "actionType");
        Long id = IdGenerator.nextId();
        jdbcTemplate.update("""
            INSERT INTO ops_alert_resolution
                (id, alert_code, action_type, comment, operator_id)
            VALUES
                (?, ?, ?, ?, ?)
            """,
            id,
            alertCode,
            actionType,
            optionalText(request, "comment"),
            operatorId
        );
        auditLogWriter.write(
            "ADMIN",
            operatorId,
            "OPS_ALERT_" + actionType,
            "OPS_ALERT",
            id,
            "SUCCESS",
            write(objectMapper, Map.of("alertCode", alertCode, "actionType", actionType))
        );
        return getResolution(id);
    }

    public PageResult<Map<String, Object>> pageResolutions(String alertCode, long pageNo, long pageSize) {
        long safePageNo = Math.max(pageNo, 1L);
        long safePageSize = Math.min(Math.max(pageSize, 1L), 100L);
        List<Object> args = new ArrayList<>();
        String where = "";
        if (StringUtils.hasText(alertCode)) {
            where = " WHERE alert_code = ?";
            args.add(alertCode.trim());
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ops_alert_resolution" + where, Long.class, args.toArray());
        args.add((safePageNo - 1) * safePageSize);
        args.add(safePageSize);
        List<Map<String, Object>> list = jdbcTemplate.query("""
            SELECT id, alert_code, action_type, comment, operator_id, created_at
            FROM ops_alert_resolution
            """ + where + """
            ORDER BY created_at DESC, id DESC
            LIMIT ?, ?
            """, (rs, rowNum) -> resolutionRow(rs), args.toArray());
        return PageResult.of(safePageNo, safePageSize, total == null ? 0L : total, list);
    }

    private Map<String, Object> findRule(String alertCode) {
        List<Map<String, Object>> list = jdbcTemplate.query("""
            SELECT r.id, r.alert_code, r.alert_name, r.severity, r.source_module,
                   r.enabled, r.notify_enabled, r.channel_id, c.channel_code, r.cooldown_minutes
            FROM ops_alert_rule r
            LEFT JOIN ops_alert_channel c ON c.id = r.channel_id
            WHERE r.alert_code = ?
            """, (rs, rowNum) -> ruleRow(rs), alertCode);
        return list.isEmpty() ? null : list.get(0);
    }

    private Map<String, Object> getChannel(String channelCode) {
        return jdbcTemplate.queryForObject("""
            SELECT id, channel_code, channel_name, channel_type, target_ref, enabled
            FROM ops_alert_channel
            WHERE channel_code = ?
            """, (rs, rowNum) -> channelRow(rs), channelCode);
    }

    private Map<String, Object> getResolution(Long id) {
        return jdbcTemplate.queryForObject("""
            SELECT id, alert_code, action_type, comment, operator_id, created_at
            FROM ops_alert_resolution
            WHERE id = ?
            """, (rs, rowNum) -> resolutionRow(rs), id);
    }

    private Map<String, Object> ruleRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleId", rs.getLong("id"));
        row.put("alertCode", rs.getString("alert_code"));
        row.put("alertName", rs.getString("alert_name"));
        row.put("severity", rs.getString("severity"));
        row.put("sourceModule", rs.getString("source_module"));
        row.put("ruleEnabled", rs.getInt("enabled") == 1);
        row.put("notifyEnabled", rs.getInt("notify_enabled") == 1);
        row.put("channelId", rs.getObject("channel_id", Long.class));
        row.put("channelCode", rs.getString("channel_code"));
        row.put("cooldownMinutes", rs.getInt("cooldown_minutes"));
        return row;
    }

    private Map<String, Object> channelRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("channelId", rs.getLong("id"));
        row.put("channelCode", rs.getString("channel_code"));
        row.put("channelName", rs.getString("channel_name"));
        row.put("channelType", rs.getString("channel_type"));
        row.put("targetRef", rs.getString("target_ref"));
        row.put("enabled", rs.getInt("enabled") == 1);
        return row;
    }

    private Map<String, Object> resolutionRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("resolutionId", rs.getLong("id"));
        row.put("alertCode", rs.getString("alert_code"));
        row.put("actionType", rs.getString("action_type"));
        row.put("comment", rs.getString("comment"));
        row.put("operatorId", rs.getObject("operator_id", Long.class));
        row.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime());
        return row;
    }

    private String requiredText(Map<String, Object> request, String key) {
        String value = optionalText(request, key);
        if (!StringUtils.hasText(value)) {
            throw new BizException(400, key + "不能为空");
        }
        return value;
    }

    private String optionalText(Map<String, Object> request, String key) {
        Object value = request.get(key);
        return value == null ? null : value.toString().trim();
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private record AlertSource(String alertCode, String countSql) {}
}
