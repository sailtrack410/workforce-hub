package com.workforcehub.attendance.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AttendanceScheduleResponse {

    private Long scheduleId;
    private Long userId;
    private LocalDate workDate;
    private Long shiftId;
    private Long calendarId;
    private String scheduleSource;
    private String status;
}
