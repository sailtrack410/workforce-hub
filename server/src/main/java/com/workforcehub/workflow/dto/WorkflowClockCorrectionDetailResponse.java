package com.workforcehub.workflow.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 补卡申请明细响应。
 */
@Data
public class WorkflowClockCorrectionDetailResponse {

    private LocalDate targetDate;
    private String missingPunchType;
    private LocalDateTime expectedPunchAt;
    private String reason;
}
