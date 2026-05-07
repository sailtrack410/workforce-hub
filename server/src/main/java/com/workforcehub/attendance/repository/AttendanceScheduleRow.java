package com.workforcehub.attendance.repository;

import java.time.LocalDate;

public record AttendanceScheduleRow(
    Long scheduleId,
    Long userId,
    LocalDate workDate,
    Long shiftId,
    Long calendarId,
    String scheduleSource,
    String status
) {
}
