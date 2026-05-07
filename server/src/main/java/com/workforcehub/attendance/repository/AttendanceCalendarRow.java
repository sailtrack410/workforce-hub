package com.workforcehub.attendance.repository;

public record AttendanceCalendarRow(
    Long calendarId,
    String calendarCode,
    String calendarName,
    String calendarType,
    String status
) {
}
