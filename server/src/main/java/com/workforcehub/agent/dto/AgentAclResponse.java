package com.workforcehub.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent ACL 响应。
 */
@Data
public class AgentAclResponse {

    private Long aclId;
    private Long agentId;
    private String subjectType;
    private Long subjectId;
    private String permCode;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
