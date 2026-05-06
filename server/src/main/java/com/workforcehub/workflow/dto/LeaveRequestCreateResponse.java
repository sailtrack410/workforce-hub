package com.workforcehub.workflow.dto;

import lombok.Data;

/**
 * 员工端请假申请提交响应。
 */
@Data
public class LeaveRequestCreateResponse {

    private Long requestId;
    private String requestNo;
    private String status;
    private String flowableProcessInstanceId;
}
