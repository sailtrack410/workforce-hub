package com.workforcehub.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "当日首签时间", example = "2026-10-14T08:57:12")
    private LocalDateTime firstPunchAt;
    @Schema(description = "当日末签时间", example = "2026-10-14T18:02:41")
    private LocalDateTime lastPunchAt;
    private LocalDateTime calculatedAt;
}
