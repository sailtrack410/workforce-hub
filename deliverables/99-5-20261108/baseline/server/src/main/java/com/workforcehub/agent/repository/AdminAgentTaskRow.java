package com.workforcehub.agent.repository;

import java.time.LocalDateTime;

public record AdminAgentTaskRow(
    Long id,
    Long agentId,
    String agentCode,
    String agentName,
    Long agentVersionId,
    String triggerType,
    String contextType,
    Long contextId,
    String contextDisplayName,
    String contextNo,
    String triggeredByType,
    Long triggeredById,
    String triggeredByName,
    String status,
    Integer toolCallCount,
    LocalDateTime waitingDeadlineAt,
    String workflowSnapshotJson,
    String executionTraceJson,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
