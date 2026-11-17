package com.workforcehub.agent.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台创建 Agent 版本请求。
 */
@Data
public class CreateAgentVersionRequest {

    @Min(value = 1, message = "版本号最小为1")
    private Integer versionNo;

    @Size(max = 64, message = "显示版本号不能超过64个字符")
    private String versionLabel;

    @Size(max = 65535, message = "模型配置不能超过65535个字符")
    private String modelConfigJson;

    @Size(max = 65535, message = "系统Prompt不能超过65535个字符")
    private String systemPrompt;

    @Size(max = 65535, message = "工作流配置不能超过65535个字符")
    private String workflowConfigJson;
}
