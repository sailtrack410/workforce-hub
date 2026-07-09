package com.workforcehub.attendance.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AttendanceGroupMemberResponse {

    private Long memberId;
    private Long groupId;
    private String groupName;
    private Long userId;
    private String employeeNo;
    private String realName;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String sourceType;
    private String sourceTypeName;
    private String status;
    private String statusName;
}
