package com.workforcehub.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateLlmModelRequest(
    @NotBlank String provider,
    String modelType,
    @NotBlank String modelName,
    @NotBlank String displayName,
    String endpoint,
    String apiKey,
    Boolean defaultFlag,
    Boolean streamOutputFlag,
    Boolean visionFlag,
    Boolean toolCallFlag,
    Boolean structuredOutputFlag,
    Boolean reasoningFlag,
    String status
) {}
