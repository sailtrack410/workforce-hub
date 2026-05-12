package com.workforcehub.im.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台 IM outbox 失败原因聚合响应。
 */
@Data
public class AdminImOutboxFailureSummaryResponse {

    private String status;
    private String lastError;
    private Long eventCount;
    private Integer maxRetryCount;
    private LocalDateTime firstOccurredAt;
    private LocalDateTime lastOccurredAt;
}
