package com.workforcehub.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批动作流水响应。
 */
@Data
public class WorkflowTaskActionResponse {

    private Long actionId;
    private Long requestId;
    private String taskId;
    private String stepKey;
    private String stepName;
    private String actorType;
    private Long actorId;
    private String actionType;
    private String comment;
    private String agentDecisionJson;
    private String beforeStatus;
    private String afterStatus;
    private LocalDateTime actedAt;
}
