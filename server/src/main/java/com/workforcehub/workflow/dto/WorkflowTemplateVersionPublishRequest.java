package com.workforcehub.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowTemplateVersionPublishRequest {

    @NotBlank(message = "flowableDefinitionKey 不能为空")
    @Size(max = 128, message = "flowableDefinitionKey 长度不能超过 128")
    private String flowableDefinitionKey;
}
