package com.workforcehub.im.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工端会话列表摘要响应。
 */
@Data
public class ImConversationSummaryResponse {

    private Long conversationId;
    private String conversationType;
    private String conversationTypeName;
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
    private String statusName;
    private Boolean pin;
    private Boolean mute;
    private String memberRole;
    private String memberRoleName;
    private Boolean hasMentionMe;
    private Boolean hasReplyMe;
    @Schema(description = "群成员数(仅群会话返回)", example = "12")
    private Long memberCount;
    @Schema(description = "保存到通讯录", example = "false")
    private Boolean saveToContacts;
    @Schema(description = "显示成员昵称", example = "true")
    private Boolean showMemberNickname;
}
