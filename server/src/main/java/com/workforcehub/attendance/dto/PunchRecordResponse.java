package com.workforcehub.attendance.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PunchRecordResponse {

    private Long punchRecordId;
    private Long userId;
    private LocalDateTime punchAt;
    private String punchType;
    private String punchTypeName;
    private String sourceType;
    private String sourceTypeName;
    private String status;
    private String statusName;
    private AttendanceDailyResultResponse dailyResult;
}
