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
                 min_host_version, max_host_version, java_version, type, status,
                 jar_path, jar_sha256, signature, signer_id,
                 frontend_entry, frontend_sha384, provider, installed_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            row.getPluginId(),
            row.getName(),
            row.getDescription(),
            row.getVersion(),
            row.getSdkVersion(),
            row.getMinHostVersion(),
            row.getMaxHostVersion(),
            row.getJavaVersion(),
            row.getType(),
            row.getStatus().name(),
            row.getJarPath(),
            row.getJarSha256(),
            row.getSignature(),
            row.getSignerId(),
            row.getFrontendEntry(),
            row.getFrontendSha384(),
            row.getProvider(),
            row.getInstalledAt(),
            row.getUpdatedAt()
        );
    }

    public LocalDateTime updateStatus(String pluginId, PluginStatus status, String errorMessage) {
        LocalDateTime updatedAt = LocalDateTime.now().withNano(0);
        jdbc.update(
            "UPDATE sys_plugin SET status = ?, error_message = ?, updated_at = ? WHERE plugin_id = ?",
            status.name(), errorMessage, updatedAt, pluginId
        );
        return updatedAt;
    }

    public LocalDateTime updateStatus(String pluginId, PluginStatus status) {
        return updateStatus(pluginId, status, null);
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
            row.setJavaVersion(rs.getString("java_version"));
            row.setType(rs.getString("type"));
            row.setStatus(PluginStatus.valueOf(rs.getString("status")));
            row.setJarPath(rs.getString("jar_path"));
            row.setJarSha256(rs.getString("jar_sha256"));
            row.setSignature(rs.getBytes("signature"));
            row.setSignerId(rs.getString("signer_id"));
            row.setFrontendEntry(rs.getString("frontend_entry"));
            row.setFrontendSha384(rs.getString("frontend_sha384"));
            row.setProvider(rs.getString("provider"));
            row.setErrorMessage(rs.getString("error_message"));
            row.setInstalledAt(rs.getTimestamp("installed_at").toLocalDateTime());
            row.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            return row;
        }
    };
}
