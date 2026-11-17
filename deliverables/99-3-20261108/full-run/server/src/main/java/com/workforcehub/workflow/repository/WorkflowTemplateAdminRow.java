package com.workforcehub.workflow.repository;

import java.time.LocalDateTime;

public record WorkflowTemplateAdminRow(
    Long id,
    Long processTypeId,
    String processCode,
    String processName,
    String templateCode,
    String templateName,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long currentVersionId,
    Integer currentVersionNo,
    String currentFlowableDefinitionKey,
    String currentFlowableDefinitionId,
    String currentVersionStatus,
    LocalDateTime currentEffectiveFrom,
    LocalDateTime currentEffectiveTo
) {
}
