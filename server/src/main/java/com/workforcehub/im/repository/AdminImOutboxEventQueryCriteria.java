package com.workforcehub.im.repository;

import java.time.LocalDateTime;

/**
 * 后台 IM outbox 查询条件。
 */
public record AdminImOutboxEventQueryCriteria(
    String status,
    String eventType,
    Long conversationId,
    Long messageId,
    Long actorUserId,
    LocalDateTime createdFrom,
    LocalDateTime createdTo
) {
}
