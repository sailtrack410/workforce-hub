package com.workforcehub.attendance.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AttendanceScheduleResponse {

    private Long scheduleId;
    private Long userId;
    private String employeeNo;
    private String realName;
    private LocalDate workDate;
    private Long shiftId;
    private String shiftName;
    private Long calendarId;
    private String calendarName;
    private String scheduleSource;
    private String scheduleSourceName;
    private String status;
    private String statusName;
}
