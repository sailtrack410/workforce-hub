package com.workforcehub.workflow.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批通过请求。
 */
@Data
public class WorkflowTaskApproveRequest {

    @Size(max = 512, message = "审批意见不能超过512个字符")
    private String comment;
}
