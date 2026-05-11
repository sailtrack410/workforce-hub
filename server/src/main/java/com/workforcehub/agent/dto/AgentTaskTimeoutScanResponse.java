package com.workforcehub.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台手动触发 Agent 人工确认超时扫描响应。
 */
@Data
public class AgentTaskTimeoutScanResponse {

    private String jobRunId;
    private String triggerType;
    private String jobStatus;
    private Integer batchSize;
    private Long scannedCount;
    private Long cancelledCount;
    private Long skippedCount;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
