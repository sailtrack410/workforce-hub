package com.workforcehub.workflow.repository;

import java.time.LocalDateTime;

/**
 * 审批申请列表读模型。
 */
public record WorkflowRequestSummaryRow(
    Long id,
    String requestNo,
    String bizType,
    Long bizId,
    String title,
    String status,
    String currentStepKey,
    String currentStepName,
    LocalDateTime submittedAt,
    LocalDateTime finishedAt
) {
}
