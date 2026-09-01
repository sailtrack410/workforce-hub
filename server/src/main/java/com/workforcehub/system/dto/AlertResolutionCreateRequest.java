package com.workforcehub.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建告警处置记录请求
 */
public record AlertResolutionCreateRequest(
    @NotBlank(message = "处置动作不能为空")
    @Pattern(regexp = "ACKNOWLEDGE|RESOLVE|IGNORE", message = "处置动作不正确") String actionType,
    @Size(max = 512) String comment
) {}
