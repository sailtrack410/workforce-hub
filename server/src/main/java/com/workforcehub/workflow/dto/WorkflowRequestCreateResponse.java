package com.workforcehub.workflow.dto;

import lombok.Data;

/**
 * 员工端审批申请提交响应。
 */
@Data
public class WorkflowRequestCreateResponse {

    private Long requestId;
    private String requestNo;
    private String status;
    private String flowableProcessInstanceId;
    private Long agentTaskId;
}
