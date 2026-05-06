package com.workforcehub.workflow.repository;

import java.time.LocalDateTime;

/**
 * 请假申请明细读模型。
 */
public record WorkflowLeaveDetailRow(
    Long leaveTypeId,
    LocalDateTime startAt,
    LocalDateTime endAt,
    Integer durationMinutes,
    String reason
) {
}
