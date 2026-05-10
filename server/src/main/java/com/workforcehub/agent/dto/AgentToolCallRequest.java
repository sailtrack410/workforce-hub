package com.workforcehub.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent 任务内的工具调用请求。
 */
@Data
public class AgentToolCallRequest {

    @NotBlank(message = "工具编码不能为空")
    @Size(max = 64, message = "工具编码不能超过64个字符")
    private String toolCode;

    @Size(max = 8192, message = "工具请求载荷不能超过8192个字符")
    private String requestPayloadJson;
}
