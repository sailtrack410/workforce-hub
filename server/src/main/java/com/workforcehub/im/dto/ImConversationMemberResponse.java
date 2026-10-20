package com.workforcehub.im.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "本群昵称", example = "小李")
    private String groupNickname;
    private LocalDateTime joinedAt;
}
