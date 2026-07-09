package com.workforcehub.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台 Agent 任务审计轨迹响应。
 */
@Data
public class AdminAgentTaskAuditResponse {

    private Long auditId;
    private Long taskId;
    private String actionType;
    private String actionTypeName;
    private String targetType;
    private String targetTypeName;
    private Long targetId;
    private String targetDisplayName;
    private String decisionSummary;
    private Boolean riskFlag;
    private LocalDateTime occurredAt;
}
