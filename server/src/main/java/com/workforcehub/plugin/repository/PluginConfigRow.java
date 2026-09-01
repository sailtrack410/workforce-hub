package com.workforcehub.plugin.repository;

import java.time.LocalDateTime;

/**
 * 插件配置记录。
 */
public record PluginConfigRow(
    String pluginId,
    String configKey,
    String configValue,
    String valueType,
    String defaultValue,
    String description,
    boolean requiresRestart,
    boolean secret,
    boolean deleted,
    long version,
    LocalDateTime updatedAt
) {}
