package com.workforcehub.im.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工端会话成员响应。
 */
@Data
public class ImConversationMemberResponse {

    private Long memberId;
    private String memberType;
    private String memberRole;
    private String status;
    private LocalDateTime joinedAt;
}
