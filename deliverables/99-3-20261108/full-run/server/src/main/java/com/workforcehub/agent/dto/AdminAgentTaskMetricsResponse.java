package com.workforcehub.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台 Agent 任务运行指标响应。
 */
@Data
public class AdminAgentTaskMetricsResponse {

    private Long totalTaskCount;
    private Long pendingTaskCount;
    private Long runningTaskCount;
    private Long succeededTaskCount;
    private Long failedTaskCount;
    private Long waitingHumanConfirmTaskCount;
    private Long rejectedTaskCount;
    private Long cancelledTaskCount;
    private Long totalToolCallCount;
    private Long failedToolCallCount;
    private Long averageFinishedDurationMillis;
    private LocalDateTime lastTaskCreatedAt;
}
