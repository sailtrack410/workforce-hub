package com.workforcehub.agent.repository;

import java.time.LocalDateTime;

public record AgentRow(
    Long id,
    String agentCode,
    String agentName,
    String agentType,
    String identityMode,
    Long publishedVersionId,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
