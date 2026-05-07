package com.workforcehub.attendance.repository;

import java.time.LocalDate;

public record AttendanceGroupMemberRow(
    Long memberId,
    Long groupId,
    Long userId,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String sourceType,
    String status
) {
}
