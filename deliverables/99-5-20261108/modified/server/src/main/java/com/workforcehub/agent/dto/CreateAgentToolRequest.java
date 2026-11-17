package com.workforcehub.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台创建 Agent 工具请求。
 */
@Data
public class CreateAgentToolRequest {

    @NotBlank(message = "工具编码不能为空")
    @Size(max = 64, message = "工具编码不能超过64个字符")
    private String toolCode;

    @NotBlank(message = "工具名称不能为空")
    @Size(max = 128, message = "工具名称不能超过128个字符")
    private String toolName;

    @NotBlank(message = "工具类型不能为空")
    private String toolType;

    private Long connectorId;

    @NotBlank(message = "风险等级不能为空")
    private String riskLevel;

    private Boolean readOnlyFlag;

    @Size(max = 32, message = "状态不能超过32个字符")
    private String status;
}
