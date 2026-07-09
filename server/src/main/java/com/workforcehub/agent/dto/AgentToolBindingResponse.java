package com.workforcehub.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 版本工具绑定响应。
 */
@Data
public class AgentToolBindingResponse {

    private Long bindingId;
    private Long agentVersionId;
    private Long toolId;
    private String toolCode;
    private String toolName;
    private String riskLevel;
    private String riskLevelName;
    private String approvalMode;
    private String approvalModeName;
    private String status;
    private String statusName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
