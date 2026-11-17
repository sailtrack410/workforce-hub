package com.workforcehub.file.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 文件中心用量账本读写服务。
 */
// 用量台账：记录每个用户/群的已用字节数和文件数，用于配额校验和统计
@Service
public class StorageUsageLedgerService {

    private final JdbcTemplate jdbcTemplate;

    public StorageUsageLedgerService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long usedBytes(String scopeType, Long scopeId) {
        Long used = jdbcTemplate.query("""
            SELECT used_bytes FROM sys_storage_usage
            WHERE scope_type = ? AND ((scope_id IS NULL AND ? IS NULL) OR scope_id = ?)
            """,
            rs -> rs.next() ? rs.getLong("used_bytes") : null,
            scopeType,
            scopeId,
            scopeId
        );
        return used == null ? 0L : used;
    }

    public long cacheUsedBytes(String scopeType, Long scopeId) {
        Long used = jdbcTemplate.query("""
            SELECT cache_used_bytes FROM sys_storage_usage
            WHERE scope_type = ? AND ((scope_id IS NULL AND ? IS NULL) OR scope_id = ?)
            """,
            rs -> rs.next() ? rs.getLong("cache_used_bytes") : null,
            scopeType,
            scopeId,
            scopeId
        );
        return used == null ? 0L : used;
    }

    public void increaseUsage(String scopeType, Long scopeId, Long bytes, int fileCount) {
        jdbcTemplate.update("""
            INSERT INTO sys_storage_usage
                (id, scope_type, scope_id, used_bytes, cache_used_bytes, file_count)
            VALUES
                (?, ?, ?, ?, 0, ?)
            ON DUPLICATE KEY UPDATE
                used_bytes = used_bytes + VALUES(used_bytes),
                file_count = file_count + VALUES(file_count)
            """, com.workforcehub.common.util.IdGenerator.nextId(), scopeType, scopeId, bytes, fileCount);
    }

    public void releaseUsage(String scopeType, Long scopeId, Long bytes, int fileCount) {
        jdbcTemplate.update("""
            UPDATE sys_storage_usage
            SET used_bytes = GREATEST(used_bytes - ?, 0),
                file_count = GREATEST(file_count - ?, 0)
            WHERE scope_type = ? AND ((scope_id IS NULL AND ? IS NULL) OR scope_id = ?)
            """,
            bytes,
            fileCount,
            scopeType,
            scopeId,
            scopeId
        );
    }
}
