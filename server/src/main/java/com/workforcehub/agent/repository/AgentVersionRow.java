package com.workforcehub.agent.repository;

import java.time.LocalDateTime;

public record AgentVersionRow(
    Long id,
    Long agentId,
    Integer versionNo,
    String modelConfigJson,
    String systemPrompt,
    String workflowConfigJson,
    String publishStatus,
    LocalDateTime publishedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
