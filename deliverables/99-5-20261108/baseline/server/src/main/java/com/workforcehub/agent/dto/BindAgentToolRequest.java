package com.workforcehub.agent.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台绑定 Agent 版本工具请求。
 */
@Data
public class BindAgentToolRequest {

    @NotNull(message = "工具ID不能为空")
    private Long toolId;

    @Size(max = 32, message = "审批模式不能超过32个字符")
    private String approvalMode;

    @Size(max = 4096, message = "参数Schema不能超过4096个字符")
    private String argSchemaJson;

    @Size(max = 4096, message = "风控配置不能超过4096个字符")
    private String riskControlJson;

    @Size(max = 32, message = "状态不能超过32个字符")
    private String status;
}
