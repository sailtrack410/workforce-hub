package com.workforcehub.agent.repository;

import java.time.LocalDateTime;

public record AgentAclRow(
    Long id,
    Long agentId,
    String subjectType,
    Long subjectId,
    String subjectName,
    String subjectCode,
    String permCode,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
