package com.workforcehub.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkflowValidateRequest {

    @NotBlank(message = "工作流配置不能为空")
    private String workflowConfigJson;
}
