package com.workforcehub.attendance.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceDailyResultRow(
    Long dailyResultId,
    Long userId,
    LocalDate workDate,
    Long scheduleId,
    Long groupId,
    Long shiftId,
    String resultStatus,
    Integer lateMinutes,
    Integer earlyLeaveMinutes,
    Integer absenceMinutes,
    Integer workMinutes,
    Boolean exceptionFlag,
    LocalDateTime calculatedAt
) {
}
