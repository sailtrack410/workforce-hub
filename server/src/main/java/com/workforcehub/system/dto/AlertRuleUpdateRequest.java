package com.workforcehub.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 更新告警规则请求
 */
public record AlertRuleUpdateRequest(
    @NotNull(message = "enabled不能为空") Boolean enabled,
    @NotNull(message = "notifyEnabled不能为空") Boolean notifyEnabled,
    Long channelId,
    @NotNull(message = "冷却分钟数不能为空") @Min(value = 0, message = "冷却分钟数不正确") Integer cooldownMinutes
) {}
