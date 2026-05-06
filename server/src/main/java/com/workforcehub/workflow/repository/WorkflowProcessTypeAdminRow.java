package com.workforcehub.workflow.repository;

import java.time.LocalDateTime;

public record WorkflowProcessTypeAdminRow(
    Long id,
    String processCode,
    String processName,
    String bizType,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
