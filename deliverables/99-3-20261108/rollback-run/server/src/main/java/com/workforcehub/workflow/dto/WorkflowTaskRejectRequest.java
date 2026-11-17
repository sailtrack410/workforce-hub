package com.workforcehub.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批驳回请求。
 */
@Data
public class WorkflowTaskRejectRequest {

    @NotBlank(message = "驳回原因不能为空")
    @Size(max = 512, message = "驳回原因不能超过512个字符")
    private String comment;
}
