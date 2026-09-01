package com.workforcehub.plugin.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * HTTP 插件动作幂等数据访问。
 */
@Repository
public class PluginActionIdempotencyRepository {

    private final JdbcTemplate jdbc;

    public PluginActionIdempotencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertClaim(
            String pluginId,
            String actionCode,
            String callerType,
            Long callerId,
            String idempotencyKey,
            String requestHash,
            String claimToken) {
        jdbc.update("""
            INSERT INTO sys_plugin_action_idempotency
                (plugin_id, action_code, caller_type, caller_id, idempotency_key,
                 request_hash, status, claim_token)
            VALUES (?, ?, ?, ?, ?, ?, 'IN_PROGRESS', ?)
            """,
            pluginId,
            actionCode,
            callerType,
            callerId,
            idempotencyKey,
            requestHash,
            claimToken
        );
    }

    public Optional<PluginActionIdempotencyRow> findForUpdate(
            String pluginId,
            String actionCode,
            String callerType,
            Long callerId,
            String idempotencyKey) {
        List<PluginActionIdempotencyRow> rows = jdbc.query("""
            SELECT plugin_id,
                   action_code,
                   caller_type,
                   caller_id,
                   idempotency_key,
                   request_hash,
                   status,
                   CAST(response_json AS CHAR) AS response_json,
                   expires_at IS NOT NULL
                       AND expires_at <= CURRENT_TIMESTAMP(6) AS expired
            FROM sys_plugin_action_idempotency
            WHERE plugin_id = ?
              AND action_code = ?
              AND caller_type = ?
              AND caller_id = ?
              AND idempotency_key = ?
            FOR UPDATE
            """,
            (rs, rowNum) -> new PluginActionIdempotencyRow(
                rs.getString("plugin_id"),
                rs.getString("action_code"),
                rs.getString("caller_type"),
                rs.getLong("caller_id"),
                rs.getString("idempotency_key"),
                rs.getString("request_hash"),
                rs.getString("status"),
                rs.getString("response_json"),
                rs.getBoolean("expired")
            ),
            pluginId,
            actionCode,
            callerType,
            callerId,
            idempotencyKey
        );
        return rows.stream().findFirst();
    }

    public int reclaimCompleted(
            PluginActionIdempotencyRow row,
            String requestHash,
            String claimToken) {
        return jdbc.update("""
            UPDATE sys_plugin_action_idempotency
            SET request_hash = ?,
                status = 'IN_PROGRESS',
                claim_token = ?,
                response_json = NULL,
                created_at = CURRENT_TIMESTAMP(6),
                expires_at = NULL
            WHERE plugin_id = ?
              AND action_code = ?
              AND caller_type = ?
              AND caller_id = ?
              AND idempotency_key = ?
              AND status = 'COMPLETED'
              AND expires_at <= CURRENT_TIMESTAMP(6)
            """,
            requestHash,
            claimToken,
            row.pluginId(),
            row.actionCode(),
            row.callerType(),
            row.callerId(),
            row.idempotencyKey()
        );
    }

    public int complete(
            ClaimScope scope,
            String requestHash,
            String claimToken,
            String responseJson,
            long responseTtlSeconds) {
        return jdbc.update("""
            UPDATE sys_plugin_action_idempotency
            SET status = 'COMPLETED',
                claim_token = NULL,
                response_json = CAST(? AS JSON),
                expires_at = TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP(6))
            WHERE plugin_id = ?
              AND action_code = ?
              AND caller_type = ?
              AND caller_id = ?
              AND idempotency_key = ?
              AND request_hash = ?
              AND status = 'IN_PROGRESS'
              AND claim_token = ?
            """,
            responseJson,
            responseTtlSeconds,
            scope.pluginId(),
            scope.actionCode(),
            scope.callerType(),
            scope.callerId(),
            scope.idempotencyKey(),
            requestHash,
            claimToken
        );
    }

    public int release(ClaimScope scope, String requestHash, String claimToken) {
        return jdbc.update("""
            DELETE FROM sys_plugin_action_idempotency
            WHERE plugin_id = ?
              AND action_code = ?
              AND caller_type = ?
              AND caller_id = ?
              AND idempotency_key = ?
              AND request_hash = ?
              AND status = 'IN_PROGRESS'
              AND claim_token = ?
            """,
            scope.pluginId(),
            scope.actionCode(),
            scope.callerType(),
            scope.callerId(),
            scope.idempotencyKey(),
            requestHash,
            claimToken
        );
    }

    public int deleteExpired(int batchSize) {
        return jdbc.update("""
            DELETE FROM sys_plugin_action_idempotency
            WHERE status = 'COMPLETED'
              AND expires_at <= CURRENT_TIMESTAMP(6)
            LIMIT ?
            """,
            batchSize
        );
    }

    public record ClaimScope(
        String pluginId,
        String actionCode,
        String callerType,
        Long callerId,
        String idempotencyKey
    ) {
    }
}
