package com.workforcehub.system.repository;

import com.workforcehub.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class NotificationChannelRepository {

    private final JdbcTemplate jdbcTemplate;

    public NotificationChannelRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<NotificationChannelRow> list() {
        return jdbcTemplate.query(selectSql() + """
            WHERE is_deleted = 0
            ORDER BY channel_code ASC, id ASC
            """, (rs, rowNum) -> row(rs));
    }

    public NotificationChannelRow findByCode(String channelCode) {
        List<NotificationChannelRow> rows = jdbcTemplate.query(selectSql() + """
            WHERE channel_code = ? AND is_deleted = 0
            """, (rs, rowNum) -> row(rs), channelCode);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public NotificationChannelRow ensureEmailDefault() {
        NotificationChannelRow existing = findByCode("EMAIL_DEFAULT");
        if (existing != null) {
            return existing;
        }
        jdbcTemplate.update("""
            INSERT INTO notify_channel_config
                (id, channel_code, channel_type, channel_name, enabled, timeout_millis)
            VALUES
                (?, 'EMAIL_DEFAULT', 'EMAIL', '邮件', 0, 5000)
            """, IdGenerator.nextId());
        return findByCode("EMAIL_DEFAULT");
    }

    public void updateEmail(NotificationChannelRow existing,
                            boolean enabled,
                            String host,
                            Integer port,
                            String username,
                            String secretValue,
                            String fromAddress,
                            String fromName,
                            boolean sslEnabled,
                            boolean starttlsEnabled,
                            Integer timeoutMillis,
                            Long operatorId) {
        jdbcTemplate.update("""
            UPDATE notify_channel_config
            SET enabled = ?,
                host = ?,
                port = ?,
                username = ?,
                secret_value = ?,
                from_address = ?,
                from_name = ?,
                ssl_enabled = ?,
                starttls_enabled = ?,
                timeout_millis = ?,
                updated_by = ?
            WHERE id = ?
            """,
            enabled ? 1 : 0,
            host,
            port,
            username,
            secretValue,
            fromAddress,
            fromName,
            sslEnabled ? 1 : 0,
            starttlsEnabled ? 1 : 0,
            timeoutMillis,
            operatorId,
            existing.id()
        );
    }

    public void updateTestResult(Long channelId, boolean success, String message) {
        jdbcTemplate.update("""
            UPDATE notify_channel_config
            SET last_test_success = ?,
                last_test_message = ?,
                last_tested_at = NOW()
            WHERE id = ?
            """, success ? 1 : 0, message, channelId);
    }

    private String selectSql() {
        return """
            SELECT id, channel_code, channel_type, channel_name, enabled,
                   host, port, username, secret_value, from_address, from_name,
                   ssl_enabled, starttls_enabled, timeout_millis,
                   last_test_success, last_test_message, last_tested_at, updated_at
            FROM notify_channel_config
            """;
    }

    private NotificationChannelRow row(ResultSet rs) throws SQLException {
        Boolean lastTestSuccess = null;
        Object lastTestSuccessValue = rs.getObject("last_test_success");
        if (lastTestSuccessValue != null) {
            lastTestSuccess = rs.getInt("last_test_success") == 1;
        }
        return new NotificationChannelRow(
            rs.getLong("id"),
            rs.getString("channel_code"),
            rs.getString("channel_type"),
            rs.getString("channel_name"),
            rs.getInt("enabled") == 1,
            rs.getString("host"),
            rs.getObject("port", Integer.class),
            rs.getString("username"),
            rs.getString("secret_value"),
            rs.getString("from_address"),
            rs.getString("from_name"),
            rs.getInt("ssl_enabled") == 1,
            rs.getInt("starttls_enabled") == 1,
            rs.getObject("timeout_millis", Integer.class),
            lastTestSuccess,
            rs.getString("last_test_message"),
            rs.getTimestamp("last_tested_at") == null ? null : rs.getTimestamp("last_tested_at").toLocalDateTime(),
            rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}
