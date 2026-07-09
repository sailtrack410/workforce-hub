package com.workforcehub.agent.repository;

import java.time.LocalDateTime;

public record AiProviderRow(
    Long id,
    String providerType,
    String displayName,
    String baseUrl,
    String apiKey,
    Boolean enabled,
    Boolean lastTestSuccess,
    String lastTestMessage,
    LocalDateTime lastTestedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
