package com.workforcehub.attendance.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceDailyResultRow(
    Long dailyResultId,
    Long userId,
    String employeeNo,
    String realName,
    LocalDate workDate,
    Long scheduleId,
    Long groupId,
    String groupName,
    Long shiftId,
    String shiftName,
    String resultStatus,
    Integer lateMinutes,
    Integer earlyLeaveMinutes,
    Integer absenceMinutes,
    Integer workMinutes,
    Boolean exceptionFlag,
    LocalDateTime calculatedAt
) {
}
