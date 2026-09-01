package com.workforcehub.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建告警通知渠道请求
 */
public record AlertChannelSaveRequest(
    @NotBlank(message = "渠道编码不能为空") @Size(max = 64) String channelCode,
    @NotBlank(message = "渠道名称不能为空") @Size(max = 128) String channelName,
    @NotBlank(message = "渠道类型不能为空")
    @Pattern(regexp = "IN_APP|WEBHOOK|EMAIL", message = "渠道类型不正确") String channelType,
    @Size(max = 512) String targetRef,
    boolean enabled
) {}
