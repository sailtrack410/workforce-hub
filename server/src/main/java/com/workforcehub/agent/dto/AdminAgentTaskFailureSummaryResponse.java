package com.workforcehub.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台 Agent 任务失败原因聚合响应。
 */
@Data
public class AdminAgentTaskFailureSummaryResponse {

    private String errorMessage;
    private Long taskCount;
    private Long toolCallCount;
    private LocalDateTime firstOccurredAt;
    private LocalDateTime lastOccurredAt;
}
