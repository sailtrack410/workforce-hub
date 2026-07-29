package com.workforcehub.plugin.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 插件写操作幂等账本数据访问。
 */
@Repository
public class PluginOperationIdempotencyRepository {

    private final JdbcTemplate jdbc;

    public PluginOperationIdempotencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<PluginOperationIdempotencyRow> find(String pluginId, String operationCode, String idempotencyKey) {
        List<PluginOperationIdempotencyRow> rows = jdbc.query(
            "SELECT * FROM sys_plugin_operation_idempotency WHERE plugin_id = ? AND operation_code = ? AND idempotency_key = ? FOR UPDATE",
            ROW_MAPPER, pluginId, operationCode, idempotencyKey
        );
        return rows.stream().findFirst();
    }

    public void insert(String pluginId, String operationCode, String idempotencyKey, String requestHash) {
        jdbc.update(
            "INSERT INTO sys_plugin_operation_idempotency (plugin_id, operation_code, idempotency_key, request_hash) VALUES (?, ?, ?, ?)",
            pluginId, operationCode, idempotencyKey, requestHash
        );
    }

    private static final RowMapper<PluginOperationIdempotencyRow> ROW_MAPPER = (rs, rowNum) ->
        new PluginOperationIdempotencyRow(
            rs.getString("plugin_id"),
            rs.getString("operation_code"),
            rs.getString("idempotency_key"),
            rs.getString("request_hash"),
            rs.getTimestamp("created_at").toLocalDateTime()
        );
}
