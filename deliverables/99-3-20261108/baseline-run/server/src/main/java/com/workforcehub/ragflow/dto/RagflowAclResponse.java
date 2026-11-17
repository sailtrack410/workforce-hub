package com.workforcehub.ragflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RagflowAclResponse {
    private Long aclId;
    private Long spaceId;
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
}
