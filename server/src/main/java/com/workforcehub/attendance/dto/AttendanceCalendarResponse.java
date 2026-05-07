package com.workforcehub.attendance.dto;

import lombok.Data;

@Data
public class AttendanceCalendarResponse {

    private Long calendarId;
    private String calendarCode;
    private String calendarName;
    private String calendarType;
    private String status;
}
