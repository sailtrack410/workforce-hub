package com.workforcehub.workflow.repository;

import java.time.LocalDateTime;

/**
 * 审批动作流水读模型。
 */
public record WorkflowTaskActionRow(
    Long actionId,
    Long requestId,
    String taskId,
    String stepKey,
    String stepName,
    String actorType,
    Long actorId,
    String actorName,
    String actorAccount,
    String actionType,
    String comment,
    String agentDecisionJson,
    String beforeStatus,
    String afterStatus,
    LocalDateTime actedAt
) {
}
