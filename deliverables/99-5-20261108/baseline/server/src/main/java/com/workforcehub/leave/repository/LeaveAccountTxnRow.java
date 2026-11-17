package com.workforcehub.leave.repository;

public record LeaveAccountTxnRow(
    Long id,
    Long accountId,
    String txnType,
    Integer changeMinutes,
    Integer balanceAfter,
    String sourceType,
    Long sourceId,
    String sourceTxnKey
) {
}
