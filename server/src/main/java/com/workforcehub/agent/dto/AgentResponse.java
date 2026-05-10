package com.workforcehub.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 主定义响应。
 */
@Data
public class AgentResponse {

    private Long agentId;
    private String agentCode;
    private String agentName;
    private String agentType;
    private String identityMode;
    private Long publishedVersionId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
