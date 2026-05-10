package com.workforcehub.agent.repository;

import java.time.LocalDateTime;

public record AgentTaskRow(
    Long id,
    Long agentId,
    Long agentVersionId,
    String triggerType,
    String contextType,
    Long contextId,
    String triggeredByType,
    Long triggeredById,
    LocalDateTime waitingDeadlineAt,
    String status,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
