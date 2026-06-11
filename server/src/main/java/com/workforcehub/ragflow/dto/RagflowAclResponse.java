package com.workforcehub.ragflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RagflowAclResponse {
    private Long aclId;
    private Long spaceId;
    private String subjectType;
    private Long subjectId;
    private String permCode;
    private String status;
    private LocalDateTime createdAt;
}
