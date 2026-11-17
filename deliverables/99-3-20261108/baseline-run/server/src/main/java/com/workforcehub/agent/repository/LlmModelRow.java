package com.workforcehub.agent.repository;

import java.time.LocalDateTime;

public record LlmModelRow(
    Long id,
    String provider,
    String modelType,
    String modelName,
    String displayName,
    String endpoint,
    String apiKey,
    Boolean defaultFlag,
    Boolean streamOutputFlag,
    Boolean visionFlag,
    Boolean toolCallFlag,
    Boolean structuredOutputFlag,
    Boolean reasoningFlag,
    String status,
    Boolean lastTestSuccess,
    String lastTestMessage,
    LocalDateTime lastTestedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
