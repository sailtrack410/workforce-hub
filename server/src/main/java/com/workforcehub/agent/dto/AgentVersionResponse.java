package com.workforcehub.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 版本响应。
 */
@Data
public class AgentVersionResponse {

    private Long versionId;
    private Long agentId;
    private Integer versionNo;
    private String modelConfigJson;
    private String systemPrompt;
    private String workflowConfigJson;
    private String publishStatus;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
