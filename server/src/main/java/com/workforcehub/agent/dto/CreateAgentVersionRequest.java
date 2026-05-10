package com.workforcehub.agent.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台创建 Agent 版本请求。
 */
@Data
public class CreateAgentVersionRequest {

    @NotNull(message = "版本号不能为空")
    @Min(value = 1, message = "版本号最小为1")
    private Integer versionNo;

    @Size(max = 4096, message = "模型配置不能超过4096个字符")
    private String modelConfigJson;

    @Size(max = 8192, message = "系统Prompt不能超过8192个字符")
    private String systemPrompt;

    @Size(max = 4096, message = "工作流配置不能超过4096个字符")
    private String workflowConfigJson;
}
