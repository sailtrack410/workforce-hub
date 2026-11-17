package com.workforcehub.workflow.repository;

import java.time.LocalDateTime;

/**
 * 审批申请详情读模型。
 */
public record WorkflowRequestDetailRow(
    Long id,
    String requestNo,
    String bizType,
    Long bizId,
    Long applicantUserId,
    String applicantName,
    String applicantEmployeeNo,
    String title,
    String status,
    String currentStepKey,
    String currentStepName,
    String flowableProcessInstanceId,
    LocalDateTime submittedAt,
    LocalDateTime finishedAt
) {
}
