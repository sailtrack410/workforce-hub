package com.workforcehub.plugin.repository;

import java.time.LocalDateTime;

/**
 * 插件写操作幂等记录（不可变）。
 */
public record PluginOperationIdempotencyRow(
    String pluginId,
    String operationCode,
    String idempotencyKey,
    String requestHash,
    LocalDateTime createdAt
) {
}
