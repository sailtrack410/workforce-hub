package com.workforcehub.workflow.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 补卡申请明细读模型。
 */
public record WorkflowClockCorrectionDetailRow(
    LocalDate targetDate,
    String missingPunchType,
    LocalDateTime expectedPunchAt,
    String reason
) {
}
