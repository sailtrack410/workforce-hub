package com.workforcehub.plugin.dto;

import java.time.LocalDateTime;

public record PluginConfigResponse(
    String key,
    String valueType,
    String value,
    String defaultValue,
    String description,
    boolean requiresRestart,
    boolean secret,
    boolean hasValue,
    long version,
    LocalDateTime updatedAt
) {}
