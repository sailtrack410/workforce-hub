package com.workforcehub.attendance.dto;

import lombok.Data;

@Data
public class AttendanceGroupResponse {

    private Long groupId;
    private String groupCode;
    private String groupName;
    private Long calendarId;
    private String calendarName;
    private Long defaultShiftId;
    private String defaultShiftName;
    private Integer lateGraceMinutes;
    private Integer earlyLeaveGraceMinutes;
    private String status;
    private String statusName;
}
