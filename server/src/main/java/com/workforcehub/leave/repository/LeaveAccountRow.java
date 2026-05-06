package com.workforcehub.leave.repository;

public record LeaveAccountRow(
    Long accountId,
    Long userId,
    Long leaveTypeId,
    String leaveCode,
    String leaveName,
    String balanceMode,
    Integer availableMinutes,
    Integer frozenMinutes,
    Integer version,
    String status
) {
}
