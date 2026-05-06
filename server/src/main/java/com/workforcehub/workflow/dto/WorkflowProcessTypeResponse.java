package com.workforcehub.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowProcessTypeResponse {

    private Long processTypeId;
    private String processCode;
    private String processName;
    private String bizType;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
