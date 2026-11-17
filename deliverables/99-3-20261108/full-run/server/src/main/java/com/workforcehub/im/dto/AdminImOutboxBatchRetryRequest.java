package com.workforcehub.im.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 后台 IM outbox 批量重试请求。
 */
@Data
public class AdminImOutboxBatchRetryRequest {

    @NotEmpty(message = "outbox事件ID不能为空")
    @Size(max = 100, message = "单次批量重试最多支持100个事件")
    private List<@NotNull(message = "outbox事件ID不能为空") Long> eventIds = List.of();
}
