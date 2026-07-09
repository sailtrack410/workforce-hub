package com.workforcehub.attendance.repository;

import java.time.LocalDate;

public record AttendanceScheduleRow(
    Long scheduleId,
    Long userId,
    String employeeNo,
    String realName,
    LocalDate workDate,
    Long shiftId,
    String shiftName,
    Long calendarId,
    String calendarName,
    String scheduleSource,
    String status
) {
}
