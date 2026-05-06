package com.workforcehub.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 申请撤回请求。
 */
@Data
public class WorkflowRequestWithdrawRequest {

    @NotBlank(message = "撤回原因不能为空")
    @Size(max = 512, message = "撤回原因不能超过512个字符")
    private String comment;
}
