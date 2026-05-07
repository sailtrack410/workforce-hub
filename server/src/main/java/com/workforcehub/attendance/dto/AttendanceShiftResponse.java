package com.workforcehub.attendance.dto;

import lombok.Data;

import java.time.LocalTime;

@Data
public class AttendanceShiftResponse {

    private Long shiftId;
    private String shiftCode;
    private String shiftName;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalTime onDutyPunchStart;
    private LocalTime onDutyPunchEnd;
    private LocalTime offDutyPunchStart;
    private LocalTime offDutyPunchEnd;
    private String status;
}
