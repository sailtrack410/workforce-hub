package com.workforcehub.workflow.repository;

/**
 * 审批申请头表读模型。
 */
public record WorkflowRequestRow(
    Long id,
    String requestNo,
    String status,
    String title,
    Long applicantUserId,
    String flowableProcessInstanceId
) {
}
