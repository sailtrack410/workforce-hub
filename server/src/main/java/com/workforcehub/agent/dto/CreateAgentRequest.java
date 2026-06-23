package com.workforcehub.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台创建 Agent 主定义请求。
 */
@Data
public class CreateAgentRequest {

    @NotBlank(message = "AI助手编码不能为空")
    @Size(max = 64, message = "AI助手编码不能超过64个字符")
    private String agentCode;

    @NotBlank(message = "AI助手名称不能为空")
    @Size(max = 128, message = "AI助手名称不能超过128个字符")
    private String agentName;

    private Long avatarAttachmentRefId;

    @NotBlank(message = "AI助手类型不能为空")
    private String agentType;

    @Size(max = 32, message = "身份模式不能超过32个字符")
    private String identityMode;
}
