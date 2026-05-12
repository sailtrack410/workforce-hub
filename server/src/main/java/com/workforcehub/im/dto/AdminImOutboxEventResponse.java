package com.workforcehub.im.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台 IM WebSocket outbox 事件响应。
 */
@Data
public class AdminImOutboxEventResponse {

    private Long eventId;
    private String eventType;
    private Long conversationId;
    private Long messageId;
    private Long seqNo;
    private Long actorUserId;
    private String recipientUserIdsJson;
    private String payloadJson;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String lastError;
    private LocalDateTime claimedAt;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
