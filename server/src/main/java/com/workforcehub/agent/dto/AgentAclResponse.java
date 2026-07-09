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
    private String subjectTypeName;
    private Long subjectId;
    private String subjectName;
    private String subjectCode;
    private String permCode;
    private String permCodeName;
    private String status;
    private String statusName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
