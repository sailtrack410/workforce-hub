package com.workforcehub.attendance.repository;

import java.time.LocalDate;

public record AttendanceExceptionRow(
    Long exceptionId,
    Long dailyResultId,
    Long userId,
    LocalDate workDate,
    String exceptionType,
    String exceptionDesc,
    String status
) {
}
