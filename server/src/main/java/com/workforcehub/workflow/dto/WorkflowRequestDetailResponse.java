package com.workforcehub.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批申请详情响应。
 */
@Data
public class WorkflowRequestDetailResponse {

    private Long requestId;
    private String requestNo;
    private String bizType;
    private Long bizId;
    private Long applicantUserId;
    private String title;
    private String status;
    private String currentStepKey;
    private String currentStepName;
    private String flowableProcessInstanceId;
    private LocalDateTime submittedAt;
    private LocalDateTime finishedAt;
    private WorkflowLeaveDetailResponse leave;
    private List<WorkflowRequestAttachmentResponse> attachments = List.of();
}
