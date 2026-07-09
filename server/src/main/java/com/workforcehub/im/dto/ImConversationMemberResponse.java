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
    private String memberTypeName;
    private String displayName;
    private String memberRole;
    private String memberRoleName;
    private String status;
    private String statusName;
    private LocalDateTime joinedAt;
}
