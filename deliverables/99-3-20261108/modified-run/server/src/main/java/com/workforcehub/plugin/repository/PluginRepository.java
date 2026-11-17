package com.workforcehub.plugin.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Transactional(readOnly = true)
    public Optional<PluginRow> findById(String pluginId) {
        String sql = "SELECT * FROM sys_plugin WHERE plugin_id = ?";
        List<PluginRow> rows = jdbc.query(sql, ROW_MAPPER, pluginId);
        hydrateDependencies(rows);
        return rows.stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<PluginRow> findAll() {
        List<PluginRow> rows = jdbc.query(
            "SELECT * FROM sys_plugin ORDER BY installed_at",
            ROW_MAPPER
        );
        hydrateDependencies(rows);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<PluginRow> findByStatus(PluginStatus status) {
        List<PluginRow> rows = jdbc.query(
            "SELECT * FROM sys_plugin WHERE status = ? ORDER BY installed_at",
            ROW_MAPPER, status.name()
        );
        hydrateDependencies(rows);
        return rows;
    }

    @Transactional
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
        insertDependencies(row.getPluginId(), row.getDependencies());
    }

    @Transactional
    public boolean replaceUninstalled(
            PluginRow row,
            String previousJarPath,
            String previousSignerId) {
        int updated = jdbc.update("""
            UPDATE sys_plugin
            SET name = ?, description = ?, version = ?, sdk_version = ?,
                min_host_version = ?, max_host_version = ?, java_version = ?, type = ?,
                status = ?, jar_path = ?, jar_sha256 = ?, signature = ?, signer_id = ?,
                frontend_entry = ?, frontend_sha384 = ?, provider = ?, error_message = NULL,
                installed_at = ?, updated_at = ?
            WHERE plugin_id = ? AND status = ? AND jar_path = ? AND signer_id = ?
            """,
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
            row.getUpdatedAt(),
            row.getPluginId(),
            PluginStatus.UNINSTALLED.name(),
            previousJarPath,
            previousSignerId
        );
        if (updated != 1) {
            return false;
        }

        jdbc.update("DELETE FROM sys_plugin_operation_idempotency WHERE plugin_id = ?", row.getPluginId());
        jdbc.update("DELETE FROM sys_plugin_event_subscription WHERE plugin_id = ?", row.getPluginId());
        jdbc.update("DELETE FROM sys_plugin_dependency WHERE plugin_id = ?", row.getPluginId());
        insertDependencies(row.getPluginId(), row.getDependencies());
        return true;
    }

    public List<String> findDependentPluginIds(String pluginId) {
        return jdbc.queryForList("""
            SELECT d.plugin_id
            FROM sys_plugin_dependency d
            JOIN sys_plugin p ON p.plugin_id = d.plugin_id
            WHERE d.depends_on = ? AND p.status <> ?
            ORDER BY d.plugin_id
            """, String.class, pluginId, PluginStatus.UNINSTALLED.name());
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

    public boolean isJarPathReferencedByAnotherPlugin(String jarPath, String pluginId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin WHERE jar_path = ? AND plugin_id <> ?",
            Integer.class,
            jarPath,
            pluginId
        );
        return count != null && count > 0;
    }

    private void hydrateDependencies(List<PluginRow> rows) {
        if (rows.isEmpty()) {
            return;
        }

        Object[] pluginIds = rows.stream()
            .map(PluginRow::getPluginId)
            .toArray();
        String placeholders = String.join(", ", Collections.nCopies(rows.size(), "?"));
        List<PersistedDependency> persisted = jdbc.query("""
            SELECT plugin_id, depends_on, min_version, max_version
            FROM sys_plugin_dependency
            WHERE plugin_id IN (%s)
            ORDER BY plugin_id, depends_on
            """.formatted(placeholders), DEPENDENCY_ROW_MAPPER, pluginIds);

        Map<String, List<PluginDependencySpec>> dependenciesByPlugin = new HashMap<>();
        for (PersistedDependency dependency : persisted) {
            dependenciesByPlugin.computeIfAbsent(
                dependency.pluginId(), ignored -> new ArrayList<>()
            ).add(dependency.spec());
        }
        for (PluginRow row : rows) {
            row.setDependencies(dependenciesByPlugin.getOrDefault(row.getPluginId(), List.of()));
        }
    }

    private void insertDependencies(String pluginId, List<PluginDependencySpec> dependencies) {
        if (dependencies.isEmpty()) {
            return;
        }

        List<Object[]> batchArgs = new ArrayList<>(dependencies.size());
        for (PluginDependencySpec dependency : dependencies) {
            batchArgs.add(new Object[] {
                pluginId,
                dependency.pluginId(),
                dependency.minVersion(),
                dependency.maxVersion()
            });
        }
        jdbc.batchUpdate("""
            INSERT INTO sys_plugin_dependency
                (plugin_id, depends_on, min_version, max_version)
            VALUES (?, ?, ?, ?)
            """, batchArgs);
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

    private static final RowMapper<PersistedDependency> DEPENDENCY_ROW_MAPPER =
            (rs, rowNum) -> new PersistedDependency(
                rs.getString("plugin_id"),
                new PluginDependencySpec(
                    rs.getString("depends_on"),
                    rs.getString("min_version"),
                    rs.getString("max_version")
                )
            );

    private record PersistedDependency(String pluginId, PluginDependencySpec spec) {
    }
}
