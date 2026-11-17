package com.workforcehub.im.repository;

import java.time.LocalDateTime;

/**
 * 后台 IM outbox 总览聚合行。
 */
public record AdminImOutboxSummaryAggregateRow(
    Long totalCount,
    Long dueDispatchCount,
    Long staleSendingCount,
    LocalDateTime earliestCreatedAt,
    LocalDateTime earliestNextRetryAt
) {
}
