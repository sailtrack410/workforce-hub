package com.workforcehub.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 工具响应。
 */
@Data
public class AgentToolResponse {

    private Long toolId;
    private String toolCode;
    private String toolName;
    private String toolType;
    private String toolTypeName;
    private Long connectorId;
    private String riskLevel;
    private String riskLevelName;
    private Boolean readOnlyFlag;
    private String status;
    private String statusName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
