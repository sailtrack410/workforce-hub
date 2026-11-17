package com.workforcehub.workflow.repository;

import java.time.LocalDateTime;

public record WorkflowTemplateVersionAdminRow(
    Long id,
    Long templateId,
    Integer versionNo,
    String flowableDefinitionKey,
    String flowableDefinitionId,
    String formSchemaJson,
    String ruleSnapshotJson,
    Long publishedBy,
    LocalDateTime publishedAt,
    LocalDateTime effectiveFrom,
    LocalDateTime effectiveTo,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
