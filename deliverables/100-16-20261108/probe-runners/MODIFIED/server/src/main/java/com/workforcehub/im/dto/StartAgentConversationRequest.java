package com.workforcehub.im.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 员工端发起 Agent 会话请求。
 */
@Data
public class StartAgentConversationRequest {

    @NotNull(message = "AI助手ID不能为空")
    private Long agentId;
}
