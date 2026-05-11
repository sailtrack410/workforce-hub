package com.workforcehub.agent.repository;

import java.time.LocalDateTime;

public record AdminAgentTaskMetricsRow(
    Long totalTaskCount,
    Long pendingTaskCount,
    Long runningTaskCount,
    Long succeededTaskCount,
    Long failedTaskCount,
    Long waitingHumanConfirmTaskCount,
    Long rejectedTaskCount,
    Long cancelledTaskCount,
    Long totalToolCallCount,
    Long failedToolCallCount,
    Long averageFinishedDurationMillis,
    LocalDateTime lastTaskCreatedAt
) {
}
