package com.workforcehub.agent.repository;

public record KnowledgeSpaceHealthRow(
    Long id,
    String spaceName,
    String status
) {
}
