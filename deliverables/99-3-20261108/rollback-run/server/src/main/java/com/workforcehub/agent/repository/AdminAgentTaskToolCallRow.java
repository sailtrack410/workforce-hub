package com.workforcehub.agent.repository;

import java.time.LocalDateTime;

public record AdminAgentTaskToolCallRow(
    Long id,
    Long taskId,
    Long toolId,
    String toolCode,
    String toolName,
    String toolType,
    String riskLevel,
    Boolean readOnlyFlag,
    String requestPayloadJson,
    String responsePayloadJson,
    String approvalStatus,
    String resultStatus,
    LocalDateTime calledAt
) {
}
