package com.workforcehub.attendance.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AttendanceExceptionResponse {

    private Long exceptionId;
    private Long dailyResultId;
    private Long userId;
    private LocalDate workDate;
    private String exceptionType;
    private String exceptionTypeName;
    private String exceptionDesc;
    private String status;
    private String statusName;
}
