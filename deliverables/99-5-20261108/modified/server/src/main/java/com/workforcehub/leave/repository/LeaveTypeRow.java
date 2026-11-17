package com.workforcehub.leave.repository;

import java.time.LocalDateTime;

public record LeaveTypeRow(
    Long id,
    String leaveCode,
    String leaveName,
    String balanceMode,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
