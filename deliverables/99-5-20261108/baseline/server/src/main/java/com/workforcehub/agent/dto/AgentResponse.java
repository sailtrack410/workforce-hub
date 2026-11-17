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
    private Long avatarAttachmentRefId;
    private String agentType;
    private String agentTypeName;
    private String identityMode;
    private String identityModeName;
    private Long publishedVersionId;
    private Integer publishedVersionNo;
    private String publishedVersionLabel;
    private Integer knowledgeIssueCount;
    private String knowledgeIssueMessage;
    private String status;
    private String statusName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
