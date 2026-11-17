package com.workforcehub.agent.dto;

import java.time.LocalDateTime;

public record AiProviderResponse(
    String providerId,
    String providerType,
    String providerTypeName,
    String displayName,
    String baseUrl,
    Boolean apiKeyConfigured,
    Boolean enabled,
    Boolean lastTestSuccess,
    String lastTestMessage,
    LocalDateTime lastTestedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
