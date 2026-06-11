package com.workforcehub.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 任务响应。
 */
@Data
public class AgentTaskResponse {

    private Long taskId;
    private Long agentId;
    private Long agentVersionId;
    private String triggerType;
    private String contextType;
    private Long contextId;
    private String status;
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
