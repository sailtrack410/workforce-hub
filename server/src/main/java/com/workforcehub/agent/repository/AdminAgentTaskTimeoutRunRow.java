package com.workforcehub.agent.repository;

import java.time.LocalDateTime;

public record AdminAgentTaskTimeoutRunRow(
    String jobRunId,
    String jobRunNo,
    String triggerType,
    String status,
    Integer batchSize,
    Long scannedCount,
    Long cancelledCount,
    Long skippedCount,
    String lockOwner,
    String errorMessage,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
