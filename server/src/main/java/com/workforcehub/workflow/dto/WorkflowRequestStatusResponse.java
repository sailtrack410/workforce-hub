package com.workforcehub.workflow.dto;

import lombok.Data;

/**
 * 审批申请状态变更响应。
 */
@Data
public class WorkflowRequestStatusResponse {

    private Long requestId;
    private String status;
}
