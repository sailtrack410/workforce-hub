package com.workforcehub.im.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工端会话消息响应。
 */
@Data
public class ImMessageResponse {

    private Long messageId;
    private Long conversationId;
    private Long seqNo;
    private String senderType;
    private Long senderId;
    private String messageType;
    private String contentJson;
    private String contentText;
    private Long quotedMessageId;
    private String status;
    private LocalDateTime sentAt;
}
