package com.workforcehub.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record SaveAiProviderRequest(
    @NotBlank String providerType,
    @NotBlank String displayName,
    String baseUrl,
    String apiKey,
    Boolean enabled
) {}
