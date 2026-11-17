package com.workforcehub.im.websocket;

import java.time.LocalDateTime;

/**
 * 员工端 IM WebSocket 推送事件。
 */
public record ImWebSocketEvent(
    String eventType,
    Long conversationId,
    Long messageId,
    Long seqNo,
    Long actorUserId,
    Object payload,
    LocalDateTime occurredAt
) {

    public static ImWebSocketEvent of(String eventType,
                                      Long conversationId,
                                      Long messageId,
                                      Long seqNo,
                                      Long actorUserId,
                                      Object payload) {
        return new ImWebSocketEvent(
            eventType,
            conversationId,
            messageId,
            seqNo,
            actorUserId,
            payload,
            LocalDateTime.now()
        );
    }
}
