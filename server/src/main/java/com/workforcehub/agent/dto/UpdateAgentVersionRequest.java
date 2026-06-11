package com.workforcehub.agent.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台更新 Agent 草稿版本请求。
 */
@Data
public class UpdateAgentVersionRequest {

    @Size(max = 65535, message = "模型配置不能超过65535个字符")
    private String modelConfigJson;

    @Size(max = 65535, message = "系统Prompt不能超过65535个字符")
    private String systemPrompt;

    @Size(max = 65535, message = "工作流配置不能超过65535个字符")
    private String workflowConfigJson;
}
