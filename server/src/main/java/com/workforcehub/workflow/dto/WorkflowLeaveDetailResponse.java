package com.workforcehub.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 请假申请明细响应。
 */
@Data
public class WorkflowLeaveDetailResponse {

    private Long leaveTypeId;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer durationMinutes;
    private String reason;
}
