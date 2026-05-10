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
    private String contextType;
    private Long contextId;
    private String triggeredByType;
    private Long triggeredById;
    private String status;
    private Integer toolCallCount;
    private LocalDateTime waitingDeadlineAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
