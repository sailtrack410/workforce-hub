package com.workforcehub.plugin.repository;

import java.time.LocalDateTime;

/**
 * 插件事件订阅记录（不可变）。
 */
public record PluginEventSubscriptionRow(
    String pluginId,
    String eventType,
    String actionCode,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
