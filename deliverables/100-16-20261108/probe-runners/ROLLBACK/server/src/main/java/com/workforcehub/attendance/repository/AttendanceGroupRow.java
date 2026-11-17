package com.workforcehub.attendance.repository;

public record AttendanceGroupRow(
    Long groupId,
    String groupCode,
    String groupName,
    Long calendarId,
    Long defaultShiftId,
    Integer lateGraceMinutes,
    Integer earlyLeaveGraceMinutes,
    String status
) {
}
