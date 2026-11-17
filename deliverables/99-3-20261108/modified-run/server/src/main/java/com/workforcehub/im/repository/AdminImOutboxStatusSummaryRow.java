package com.workforcehub.im.repository;

/**
 * 后台 IM outbox 状态计数行。
 */
public record AdminImOutboxStatusSummaryRow(
    String status,
    Long eventCount
) {
}
