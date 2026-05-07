package com.workforcehub.attendance.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AttendanceGroupMemberResponse {

    private Long memberId;
    private Long groupId;
    private Long userId;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String sourceType;
    private String status;
}
