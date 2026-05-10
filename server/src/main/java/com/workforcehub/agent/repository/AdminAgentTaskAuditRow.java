package com.workforcehub.agent.repository;

import java.time.LocalDateTime;

public record AdminAgentTaskAuditRow(
    Long id,
    Long taskId,
    String actionType,
    String targetType,
    Long targetId,
    String decisionSummary,
    Boolean riskFlag,
    LocalDateTime occurredAt
) {
}
