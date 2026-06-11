package com.workforcehub.agent.repository;

import java.time.LocalDateTime;

public record LlmModelRow(
    Long id,
    String provider,
    String modelName,
    String displayName,
    String endpoint,
    String apiKey,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
