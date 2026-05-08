package com.workforcehub.spi;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤补卡落地接口。
 * 由 module-attendance 实现，workflow 审批通过后通过 SPI 写入人工补卡事实并重算日结果。
 */
public interface AttendanceClockCorrectionService {

    /**
     * 应用已审批通过的补卡申请。
     *
     * @param userId 员工用户ID
     * @param targetDate 补卡考勤日期
     * @param punchType 补卡类型: ON_DUTY/OFF_DUTY
     * @param expectedPunchAt 补卡时间
     * @param requestId 审批申请ID
     * @param approverUserId 审批人用户ID
     */
    void applyApprovedClockCorrection(Long userId,
                                      LocalDate targetDate,
                                      String punchType,
                                      LocalDateTime expectedPunchAt,
                                      Long requestId,
                                      Long approverUserId);
}
