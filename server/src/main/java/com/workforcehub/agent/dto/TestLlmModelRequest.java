package com.workforcehub.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record TestLlmModelRequest(
    @NotBlank String provider,
    String modelType,
    @NotBlank String modelName,
    String endpoint,
    String apiKey,
    Boolean visionFlag
) {
    public TestLlmModelRequest(String provider, String modelType, String modelName, String endpoint, String apiKey) {
        this(provider, modelType, modelName, endpoint, apiKey, null);
    }
}
