package com.workforcehub.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工端审批申请列表项。
 */
@Data
public class WorkflowRequestSummaryResponse {

    private Long requestId;
    private String requestNo;
    private String bizType;
    private String bizTypeName;
    private Long bizId;
    private String title;
    private String status;
    private String statusName;
    private String currentStepKey;
    private String currentStepName;
    private LocalDateTime submittedAt;
    private LocalDateTime finishedAt;
}
