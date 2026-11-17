package com.workforcehub.agent.repository;

import java.time.LocalDateTime;

public record AdminAgentTaskFailureSummaryRow(
    String errorMessage,
    Long taskCount,
    Long toolCallCount,
    LocalDateTime firstOccurredAt,
    LocalDateTime lastOccurredAt
) {
}
