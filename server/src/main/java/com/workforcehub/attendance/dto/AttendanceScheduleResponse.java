package com.workforcehub.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "上班时间 HH:mm", example = "09:00")
    private String startTime;
    @Schema(description = "下班时间 HH:mm", example = "18:00")
    private String endTime;
    @Schema(description = "是否工作日", example = "true")
    private Boolean isWorkday;
}
