package com.workforcehub.attendance.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AttendanceDailyResultResponse {

    private Long dailyResultId;
    private Long userId;
    private String employeeNo;
    private String realName;
    private LocalDate workDate;
    private Long scheduleId;
    private Long groupId;
    private String groupName;
    private Long shiftId;
    private String shiftName;
    private String resultStatus;
    private String resultStatusName;
    private Integer lateMinutes;
    private Integer earlyLeaveMinutes;
    private Integer absenceMinutes;
    private Integer workMinutes;
    private Boolean exceptionFlag;
    private LocalDateTime calculatedAt;
}
