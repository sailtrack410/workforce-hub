package com.workforcehub.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowTemplateCreateRequest {

    @NotNull(message = "processTypeId 不能为空")
    private Long processTypeId;

    @NotBlank(message = "templateCode 不能为空")
    @Size(max = 64, message = "templateCode 长度不能超过 64")
    private String templateCode;

    @NotBlank(message = "templateName 不能为空")
    @Size(max = 128, message = "templateName 长度不能超过 128")
    private String templateName;

    @Size(max = 32, message = "status 长度不能超过 32")
    private String status;
}
