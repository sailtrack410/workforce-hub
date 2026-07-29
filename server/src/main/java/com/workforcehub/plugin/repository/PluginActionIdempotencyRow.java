package com.workforcehub.plugin.repository;

/**
 * HTTP 插件动作幂等记录（不可变）。
 */
public record PluginActionIdempotencyRow(
    String pluginId,
    String actionCode,
    String callerType,
    Long callerId,
    String idempotencyKey,
    String requestHash,
    String status,
    String responseJson,
    boolean expired
) {
}
