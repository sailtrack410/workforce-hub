package com.workforcehub.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowProcessTypeCreateRequest {

    @NotBlank(message = "processCode 不能为空")
    @Size(max = 64, message = "processCode 长度不能超过 64")
    private String processCode;

    @NotBlank(message = "processName 不能为空")
    @Size(max = 128, message = "processName 长度不能超过 128")
    private String processName;

    @Size(max = 64, message = "bizType 长度不能超过 64")
    private String bizType;

    @Size(max = 32, message = "status 长度不能超过 32")
    private String status;
}
