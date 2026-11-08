package com.workforcehub.file.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryIncreaseUsage(String scopeType, Long scopeId, long bytes, long quotaBytes) {
        // V4 的 scope_key 唯一键让首次创建也参与同一行锁竞争；不可先 SELECT 再判断余量。
        jdbcTemplate.update("""
            INSERT INTO sys_storage_usage
                (id, scope_type, scope_id, used_bytes, cache_used_bytes, file_count)
            VALUES (?, ?, ?, 0, 0, 0)
            ON DUPLICATE KEY UPDATE used_bytes = used_bytes
            """, com.workforcehub.common.util.IdGenerator.nextId(), scopeType, scopeId);
        return jdbcTemplate.update("""
            UPDATE sys_storage_usage
            SET used_bytes = used_bytes + ?, file_count = file_count + 1
            WHERE scope_type = ? AND scope_key = ? AND used_bytes <= ? - ?
            """, bytes, scopeType, scopeId == null ? "__NONE__" : scopeId.toString(), quotaBytes, bytes) == 1;
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
