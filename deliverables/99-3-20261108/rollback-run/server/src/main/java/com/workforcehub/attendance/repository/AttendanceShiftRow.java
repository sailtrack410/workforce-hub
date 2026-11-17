package com.workforcehub.attendance.repository;

import java.time.LocalTime;

public record AttendanceShiftRow(
    Long shiftId,
    String shiftCode,
    String shiftName,
    LocalTime startTime,
    LocalTime endTime,
    LocalTime onDutyPunchStart,
    LocalTime onDutyPunchEnd,
    LocalTime offDutyPunchStart,
    LocalTime offDutyPunchEnd,
    String status
) {
}
