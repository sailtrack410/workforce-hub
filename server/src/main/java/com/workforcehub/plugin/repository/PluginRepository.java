package com.workforcehub.plugin.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 插件注册表数据访问。
 */
@Repository
public class PluginRepository {

    private final JdbcTemplate jdbc;

    public PluginRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PluginRow> findById(String pluginId) {
        String sql = "SELECT * FROM sys_plugin WHERE plugin_id = ?";
        List<PluginRow> rows = jdbc.query(sql, ROW_MAPPER, pluginId);
        return rows.stream().findFirst();
    }

    public List<PluginRow> findAll() {
        return jdbc.query("SELECT * FROM sys_plugin ORDER BY installed_at", ROW_MAPPER);
    }

    public List<PluginRow> findByStatus(PluginStatus status) {
        return jdbc.query(
            "SELECT * FROM sys_plugin WHERE status = ? ORDER BY installed_at",
            ROW_MAPPER, status.name()
        );
    }

    public void insert(PluginRow row) {
        jdbc.update("""
            INSERT INTO sys_plugin
                (plugin_id, name, description, version, sdk_version,
                 min_host_version, max_host_version, type, status,
                 jar_path, jar_sha256, provider, installed_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            row.getPluginId(),
            row.getName(),
            row.getDescription(),
            row.getVersion(),
            row.getSdkVersion(),
            row.getMinHostVersion(),
            row.getMaxHostVersion(),
            row.getType(),
            row.getStatus().name(),
            row.getJarPath(),
            row.getJarSha256(),
            row.getProvider(),
            row.getInstalledAt(),
            row.getUpdatedAt()
        );
    }

    public void updateStatus(String pluginId, PluginStatus status, String errorMessage) {
        jdbc.update(
            "UPDATE sys_plugin SET status = ?, error_message = ?, updated_at = ? WHERE plugin_id = ?",
            status.name(), errorMessage, LocalDateTime.now(), pluginId
        );
    }

    public void updateStatus(String pluginId, PluginStatus status) {
        updateStatus(pluginId, status, null);
    }

    public void delete(String pluginId) {
        jdbc.update("DELETE FROM sys_plugin WHERE plugin_id = ?", pluginId);
    }

    public boolean exists(String pluginId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin WHERE plugin_id = ?",
            Integer.class, pluginId
        );
        return count != null && count > 0;
    }

    private static final RowMapper<PluginRow> ROW_MAPPER = new RowMapper<>() {
        @Override
        public PluginRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            PluginRow row = new PluginRow();
            row.setPluginId(rs.getString("plugin_id"));
            row.setName(rs.getString("name"));
            row.setDescription(rs.getString("description"));
            row.setVersion(rs.getString("version"));
            row.setSdkVersion(rs.getString("sdk_version"));
            row.setMinHostVersion(rs.getString("min_host_version"));
            row.setMaxHostVersion(rs.getString("max_host_version"));
            row.setType(rs.getString("type"));
            row.setStatus(PluginStatus.valueOf(rs.getString("status")));
            row.setJarPath(rs.getString("jar_path"));
            row.setJarSha256(rs.getString("jar_sha256"));
            row.setProvider(rs.getString("provider"));
            row.setErrorMessage(rs.getString("error_message"));
            row.setInstalledAt(rs.getTimestamp("installed_at").toLocalDateTime());
            row.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            return row;
        }
    };
}
