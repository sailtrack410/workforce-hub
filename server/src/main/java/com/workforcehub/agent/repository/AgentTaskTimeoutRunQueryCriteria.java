package com.workforcehub.agent.repository;

import java.time.LocalDateTime;

public record AgentTaskTimeoutRunQueryCriteria(
    String triggerType,
    String status,
    LocalDateTime startedFrom,
    LocalDateTime startedTo
) {
}
