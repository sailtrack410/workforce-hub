package com.workforcehub.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台 Agent 任务响应。
 */
@Data
public class AdminAgentTaskResponse {

    private Long taskId;
    private Long agentId;
    private String agentCode;
    private String agentName;
    private Long agentVersionId;
    private String triggerType;
    private String triggerTypeName;
    private String contextType;
    private String contextTypeName;
    private Long contextId;
    private String contextDisplayName;
    private String contextNo;
    private String triggeredByType;
    private String triggeredByTypeName;
    private Long triggeredById;
    private String triggeredByName;
    private String status;
    private String statusName;
    private Integer toolCallCount;
    private LocalDateTime waitingDeadlineAt;
    private String workflowSnapshotJson;
    private String executionTraceJson;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
