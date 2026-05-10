package com.workforcehub.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 员工端 Agent 任务取消请求。
 */
@Data
public class CancelAgentTaskRequest {

    @NotBlank(message = "取消原因不能为空")
    @Size(max = 512, message = "取消原因不能超过512个字符")
    private String reason;
}
