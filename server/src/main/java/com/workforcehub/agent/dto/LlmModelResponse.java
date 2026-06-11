package com.workforcehub.agent.dto;

import java.time.LocalDateTime;

public record LlmModelResponse(
    String modelId,
    String provider,
    String modelName,
    String displayName,
    String endpoint,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
