package com.workforcehub.agent.repository;

import java.time.LocalDateTime;

public record AgentToolRow(
    Long id,
    String toolCode,
    String toolName,
    String toolType,
    Long connectorId,
    String riskLevel,
    Boolean readOnlyFlag,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
