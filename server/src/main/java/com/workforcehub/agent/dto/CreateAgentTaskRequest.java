package com.workforcehub.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 员工端创建 Agent 任务请求。
 */
@Data
public class CreateAgentTaskRequest {

    @NotBlank(message = "触发类型不能为空")
    private String triggerType;

    @Size(max = 64, message = "上下文类型不能超过64个字符")
    private String contextType;

    private Long contextId;

    @Size(max = 8192, message = "输入载荷不能超过8192个字符")
    private String inputPayloadJson;

    @Valid
    @Size(max = 10, message = "单个任务最多调用10个工具")
    private List<AgentToolCallRequest> toolCalls = List.of();
}
