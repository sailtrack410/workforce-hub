package com.workforcehub.im.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 员工端发起私聊请求。
 */
@Data
public class StartPrivateConversationRequest {

    @NotNull(message = "目标用户不能为空")
    private Long targetUserId;
}
