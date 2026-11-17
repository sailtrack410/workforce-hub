package com.workforcehub.im.repository;

import java.time.LocalDateTime;

/**
 * 后台 IM outbox 事件查询行。
 */
public record AdminImOutboxEventRow(
    Long eventId,
    String eventType,
    Long conversationId,
    Long messageId,
    Long seqNo,
    Long actorUserId,
    String recipientUserIdsJson,
    String payloadJson,
    String status,
    Integer retryCount,
    LocalDateTime nextRetryAt,
    String lastError,
    LocalDateTime claimedAt,
    LocalDateTime sentAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
