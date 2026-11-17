package com.workforcehub.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台 Agent 任务工具调用明细响应。
 */
@Data
public class AdminAgentTaskToolCallResponse {

    private Long logId;
    private Long taskId;
    private Long toolId;
    private String toolCode;
    private String toolName;
    private String toolType;
    private String toolTypeName;
    private String riskLevel;
    private String riskLevelName;
    private Boolean readOnlyFlag;
    private String requestPayloadJson;
    private String responsePayloadJson;
    private String approvalStatus;
    private String approvalStatusName;
    private String resultStatus;
    private String resultStatusName;
    private LocalDateTime calledAt;
}
