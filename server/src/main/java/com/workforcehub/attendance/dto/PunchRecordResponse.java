package com.workforcehub.attendance.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PunchRecordResponse {

    private Long punchRecordId;
    private Long userId;
    private LocalDateTime punchAt;
    private String punchType;
    private String sourceType;
    private String status;
    private AttendanceDailyResultResponse dailyResult;
}
