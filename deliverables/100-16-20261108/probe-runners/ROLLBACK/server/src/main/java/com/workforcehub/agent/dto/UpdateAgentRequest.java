package com.workforcehub.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台更新 Agent 主定义请求。
 */
@Data
public class UpdateAgentRequest {

    @NotBlank(message = "AI助手名称不能为空")
    @Size(max = 128, message = "AI助手名称不能超过128个字符")
    private String agentName;

    private Long avatarAttachmentRefId;
}
