package com.workforcehub.im.repository;

import java.time.LocalDateTime;

public record ImMessageRow(
    Long messageId,
    Long conversationId,
    Long seqNo,
    String senderType,
    Long senderId,
    String messageType,
    String contentJson,
    Long quotedMessageId,
    String status,
    LocalDateTime sentAt
) {
}
