package com.workforcehub.agent.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 员工端 Agent 人工确认请求。
 */
@Data
public class ConfirmAgentTaskRequest {

    @NotNull(message = "确认结果不能为空")
    private Boolean approved;

    @Size(max = 512, message = "确认意见不能超过512个字符")
    private String comment;
}
