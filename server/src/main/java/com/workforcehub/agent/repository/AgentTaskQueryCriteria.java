package com.workforcehub.agent.repository;

import java.time.LocalDateTime;

public record AgentTaskQueryCriteria(
    String status,
    Long agentId,
    String triggeredByType,
    Long triggeredById,
    String contextType,
    LocalDateTime createdFrom,
    LocalDateTime createdTo
) {
}
