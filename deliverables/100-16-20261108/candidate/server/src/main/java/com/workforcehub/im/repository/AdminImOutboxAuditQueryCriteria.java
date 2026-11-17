package com.workforcehub.im.repository;

import java.time.LocalDateTime;

/**
 * 后台 IM outbox 操作审计查询条件。
 */
public record AdminImOutboxAuditQueryCriteria(
    String actionType,
    Long eventId,
    Long operatorId,
    LocalDateTime occurredFrom,
    LocalDateTime occurredTo
) {
}
