package com.workforcehub.agent.repository;

import java.time.LocalDateTime;

public record AgentAclRow(
    Long id,
    Long agentId,
    String subjectType,
    Long subjectId,
    String permCode,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
