package com.workforcehub.im.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 员工端转发会话消息请求。
 */
@Data
public class ForwardImMessageRequest {

    @NotNull(message = "目标会话不能为空")
    private Long targetConversationId;
}
