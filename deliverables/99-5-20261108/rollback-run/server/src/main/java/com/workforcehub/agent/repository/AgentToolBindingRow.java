package com.workforcehub.agent.repository;

import java.time.LocalDateTime;

public record AgentToolBindingRow(
    Long id,
    Long agentVersionId,
    Long toolId,
    String toolCode,
    String toolName,
    String toolType,
    String riskLevel,
    Boolean readOnlyFlag,
    String approvalMode,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
