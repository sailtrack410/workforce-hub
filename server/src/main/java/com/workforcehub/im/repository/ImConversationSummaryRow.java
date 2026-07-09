package com.workforcehub.im.repository;

import java.time.LocalDateTime;

public record ImConversationSummaryRow(
    Long conversationId,
    String conversationType,
    String conversationKey,
    String title,
    Long avatarFileId,
    Long boundAgentId,
    Long lastMessageSeq,
    Long lastReadSeq,
    Long unreadCount,
    LocalDateTime lastMessageAt,
    String status,
    Boolean pin,
    Boolean mute,
    String memberRole,
    Boolean hasMentionMe,
    Boolean hasReplyMe,
    ImMessageRow lastMessage
) {
}
