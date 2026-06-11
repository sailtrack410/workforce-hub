package com.workforcehub.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateLlmModelRequest(
    @NotBlank String provider,
    @NotBlank String modelName,
    @NotBlank String displayName,
    String endpoint,
    String apiKey,
    String status
) {}
