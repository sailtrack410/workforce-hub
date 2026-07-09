package com.workforcehub.attendance.repository;

import java.time.LocalDate;

public record AttendanceGroupMemberRow(
    Long memberId,
    Long groupId,
    String groupName,
    Long userId,
    String employeeNo,
    String realName,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String sourceType,
    String status
) {
}
