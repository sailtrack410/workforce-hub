package com.workforcehub.im.repository;

import java.time.LocalDateTime;

/**
 * 后台 IM outbox 操作审计查询行。
 */
public record AdminImOutboxAuditRow(
    Long auditId,
    String operatorType,
    Long operatorId,
    String actionType,
    String targetType,
    Long targetId,
    String result,
    String detailJson,
    LocalDateTime occurredAt
) {
}
