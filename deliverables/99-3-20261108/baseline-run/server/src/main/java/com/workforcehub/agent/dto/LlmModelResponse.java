package com.workforcehub.agent.dto;

import java.time.LocalDateTime;

public record LlmModelResponse(
    String modelId,
    String provider,
    String providerName,
    String modelType,
    String modelTypeName,
    String modelName,
    String displayName,
    String endpoint,
    Boolean apiKeyConfigured,
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
