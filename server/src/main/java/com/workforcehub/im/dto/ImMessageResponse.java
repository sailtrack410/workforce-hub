package com.workforcehub.im.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 员工端会话消息响应。
 */
@Data
public class ImMessageResponse {

    private Long messageId;
    private Long conversationId;
    private Long seqNo;
    private String senderType;
    private String senderTypeName;
    private Long senderId;
    private String messageType;
    private String messageTypeName;
    private String contentJson;
    private String contentText;
    private List<ImMessageAttachmentResponse> attachments = List.of();
    private List<ImMessageMentionResponse> mentions = List.of();
    private Long quotedMessageId;
    private String status;
    private String statusName;
    private LocalDateTime sentAt;
}
