package com.workforcehub.im.websocket;

/**
 * IM WebSocket outbox 待投递事件。
 */
public record ImEventOutboxRow(
    Long id,
    String eventType,
    Long conversationId,
    Long messageId,
    Long seqNo,
    Long actorUserId,
    String recipientUserIdsJson,
    String payloadJson,
    String status,
    Integer retryCount
) {
}
