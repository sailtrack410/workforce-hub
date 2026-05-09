package com.workforcehub.im.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工端会话列表摘要响应。
 */
@Data
public class ImConversationSummaryResponse {

    private Long conversationId;
    private String conversationType;
    private String conversationKey;
    private String title;
    private Long avatarFileId;
    private Long boundAgentId;
    private Long lastMessageSeq;
    private Long lastReadSeq;
    private Long unreadCount;
    private ImMessageResponse lastMessage;
    private LocalDateTime lastMessageAt;
    private String status;
    private Boolean pin;
    private Boolean mute;
    private String memberRole;
}
