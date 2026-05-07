package com.workforcehub.spi;

import java.time.LocalDate;

/**
 * 考勤日结果重算接口。
 * 由 module-attendance 实现，workflow/leave 等外部事件通过 SPI 触发重算。
 */
public interface AttendanceRecalculationService {

    /**
     * 重算指定员工指定日期的考勤日结果。
     *
     * @param userId 员工用户ID
     * @param workDate 考勤日期
     */
    void recalculateDailyResultForExternalEvent(Long userId, LocalDate workDate);
}
