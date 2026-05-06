package com.workforcehub.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowTemplateVersionResponse {

    private Long versionId;
    private Long templateId;
    private Integer versionNo;
    private String flowableDefinitionKey;
    private String flowableDefinitionId;
    private String formSchemaJson;
    private String ruleSnapshotJson;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
