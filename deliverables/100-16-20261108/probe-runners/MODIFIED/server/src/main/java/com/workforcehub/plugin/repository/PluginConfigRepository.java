package com.workforcehub.plugin.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 插件配置数据访问，所有配置操作都必须同时限定 plugin_id 和 config_key。
 */
@Repository
public class PluginConfigRepository {

    private final JdbcTemplate jdbc;

    public PluginConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PluginStatus> findPluginStatus(String pluginId) {
        List<PluginStatus> rows = jdbc.query(
            "SELECT status FROM sys_plugin WHERE plugin_id = ?",
            (rs, rowNum) -> PluginStatus.valueOf(rs.getString("status")),
            pluginId
        );
        return rows.stream().findFirst();
    }

    public Optional<PluginStatus> lockPluginStatus(String pluginId) {
        List<PluginStatus> rows = jdbc.query(
            "SELECT status FROM sys_plugin WHERE plugin_id = ? FOR UPDATE",
            (rs, rowNum) -> PluginStatus.valueOf(rs.getString("status")),
            pluginId
        );
        return rows.stream().findFirst();
    }

    public List<PluginConfigRow> findByPluginId(String pluginId, boolean includeDeleted) {
        return jdbc.query(
            SELECT_SQL + " WHERE plugin_id = ?"
                + (includeDeleted ? "" : " AND is_deleted = 0")
                + " ORDER BY config_key",
            ROW_MAPPER,
            pluginId
        );
    }

    public Optional<PluginConfigRow> find(String pluginId, String configKey) {
        List<PluginConfigRow> rows = jdbc.query(
            SELECT_SQL + " WHERE plugin_id = ? AND config_key = ? AND is_deleted = 0",
            ROW_MAPPER,
            pluginId,
            configKey
        );
        return rows.stream().findFirst();
    }

    public Optional<PluginConfigRow> findIncludingDeleted(String pluginId, String configKey) {
        List<PluginConfigRow> rows = jdbc.query(
            SELECT_SQL + " WHERE plugin_id = ? AND config_key = ?",
            ROW_MAPPER,
            pluginId,
            configKey
        );
        return rows.stream().findFirst();
    }

    public int countByPluginId(String pluginId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_config WHERE plugin_id = ?",
            Integer.class,
            pluginId
        );
        return count == null ? 0 : count;
    }

    public void insert(PluginConfigRow row) {
        jdbc.update("""
            INSERT INTO sys_plugin_config
                (plugin_id, config_key, config_value, value_type, default_value,
                 description, requires_restart, is_secret, version, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 0)
            """,
            row.pluginId(),
            row.configKey(),
            row.configValue(),
            row.valueType(),
            row.defaultValue(),
            row.description(),
            row.requiresRestart() ? 1 : 0,
            row.secret() ? 1 : 0
        );
    }

    public int update(PluginConfigRow row, long expectedVersion) {
        return jdbc.update("""
            UPDATE sys_plugin_config
            SET config_value = ?,
                value_type = ?,
                default_value = ?,
                description = ?,
                requires_restart = ?,
                is_secret = ?,
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE plugin_id = ? AND config_key = ? AND version = ?
              AND is_deleted = 0
            """,
            row.configValue(),
            row.valueType(),
            row.defaultValue(),
            row.description(),
            row.requiresRestart() ? 1 : 0,
            row.secret() ? 1 : 0,
            row.pluginId(),
            row.configKey(),
            expectedVersion
        );
    }

    public int restore(PluginConfigRow row, long deletedVersion) {
        return jdbc.update("""
            UPDATE sys_plugin_config
            SET config_value = ?,
                value_type = ?,
                default_value = ?,
                description = ?,
                requires_restart = ?,
                is_secret = ?,
                is_deleted = 0,
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE plugin_id = ? AND config_key = ? AND version = ?
              AND is_deleted = 1
            """,
            row.configValue(),
            row.valueType(),
            row.defaultValue(),
            row.description(),
            row.requiresRestart() ? 1 : 0,
            row.secret() ? 1 : 0,
            row.pluginId(),
            row.configKey(),
            deletedVersion
        );
    }

    public int delete(String pluginId, String configKey, long expectedVersion) {
        return jdbc.update("""
            UPDATE sys_plugin_config
            SET config_value = NULL,
                value_type = 'STRING',
                default_value = NULL,
                description = NULL,
                requires_restart = 0,
                is_secret = 0,
                is_deleted = 1,
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE plugin_id = ? AND config_key = ? AND version = ?
              AND is_deleted = 0
            """,
            pluginId,
            configKey,
            expectedVersion
        );
    }

    private static final String SELECT_SQL = """
        SELECT plugin_id, config_key, config_value, value_type, default_value,
               description, requires_restart, is_secret, is_deleted, version, updated_at
        FROM sys_plugin_config
        """;

    private static final RowMapper<PluginConfigRow> ROW_MAPPER = (rs, rowNum) ->
        new PluginConfigRow(
            rs.getString("plugin_id"),
            rs.getString("config_key"),
            rs.getString("config_value"),
            rs.getString("value_type"),
            rs.getString("default_value"),
            rs.getString("description"),
            rs.getInt("requires_restart") == 1,
            rs.getInt("is_secret") == 1,
            rs.getInt("is_deleted") == 1,
            rs.getLong("version"),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
}
