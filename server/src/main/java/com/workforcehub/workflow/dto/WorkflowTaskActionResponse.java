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
    private String actorTypeName;
    private Long actorId;
    private String actorName;
    private String actorAccount;
    private String actionType;
    private String actionTypeName;
    private String comment;
    private String agentDecisionJson;
    private String beforeStatus;
    private String beforeStatusName;
    private String afterStatus;
    private String afterStatusName;
    private LocalDateTime actedAt;
}
