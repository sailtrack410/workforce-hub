package com.workforcehub.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台 Agent 人工确认超时扫描批次响应。
 */
@Data
public class AdminAgentTaskTimeoutRunResponse {

    private String jobRunId;
    private String jobRunNo;
    private String triggerType;
    private String triggerTypeName;
    private String status;
    private String statusName;
    private Integer batchSize;
    private Long scannedCount;
    private Long cancelledCount;
    private Long skippedCount;
    private String lockOwner;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
