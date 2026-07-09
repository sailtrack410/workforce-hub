package com.workforcehub.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowTemplateResponse {

    private Long templateId;
    private Long processTypeId;
    private String processCode;
    private String processName;
    private String templateCode;
    private String templateName;
    private String status;
    private String statusName;
    private WorkflowTemplateVersionResponse currentVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
