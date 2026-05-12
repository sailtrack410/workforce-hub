package com.workforcehub.im.repository;

import java.time.LocalDateTime;

/**
 * 后台 IM outbox 失败原因聚合行。
 */
public record AdminImOutboxFailureSummaryRow(
    String status,
    String lastError,
    Long eventCount,
    Integer maxRetryCount,
    LocalDateTime firstOccurredAt,
    LocalDateTime lastOccurredAt
) {
}
