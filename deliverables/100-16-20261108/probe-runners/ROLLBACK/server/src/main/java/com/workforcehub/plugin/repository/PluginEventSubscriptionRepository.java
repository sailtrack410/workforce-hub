package com.workforcehub.plugin.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 插件事件订阅数据访问。
 */
@Repository
public class PluginEventSubscriptionRepository {

    private final JdbcTemplate jdbc;

    public PluginEventSubscriptionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PluginEventSubscriptionRow> find(String pluginId, String eventType) {
        List<PluginEventSubscriptionRow> rows = jdbc.query(
            "SELECT * FROM sys_plugin_event_subscription WHERE plugin_id = ? AND event_type = ?",
            ROW_MAPPER, pluginId, eventType
        );
        return rows.stream().findFirst();
    }

    public List<PluginEventSubscriptionRow> findByEventType(String eventType) {
        return jdbc.query(
            "SELECT * FROM sys_plugin_event_subscription WHERE event_type = ? ORDER BY plugin_id",
            ROW_MAPPER, eventType
        );
    }

    public void save(String pluginId, String eventType, String actionCode) {
        jdbc.update("""
            INSERT INTO sys_plugin_event_subscription (plugin_id, event_type, action_code)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE action_code = VALUES(action_code), updated_at = CURRENT_TIMESTAMP
            """, pluginId, eventType, actionCode);
    }

    public void delete(String pluginId, String eventType) {
        jdbc.update(
            "DELETE FROM sys_plugin_event_subscription WHERE plugin_id = ? AND event_type = ?",
            pluginId, eventType
        );
    }

    private static final RowMapper<PluginEventSubscriptionRow> ROW_MAPPER = (rs, rowNum) ->
        new PluginEventSubscriptionRow(
            rs.getString("plugin_id"),
            rs.getString("event_type"),
            rs.getString("action_code"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
}
