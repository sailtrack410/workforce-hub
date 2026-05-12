package com.workforcehub.im.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台 IM outbox 状态总览响应。
 */
@Data
public class AdminImOutboxSummaryResponse {

    private Long totalCount;
    private Long dueDispatchCount;
    private Long staleSendingCount;
    private LocalDateTime earliestCreatedAt;
    private LocalDateTime earliestNextRetryAt;
    private LocalDateTime generatedAt;
    private LocalDateTime sendingStaleBefore;
    private List<StatusCount> statusCounts;

    @Data
    public static class StatusCount {
        private String status;
        private Long count;

        public static StatusCount of(String status, Long count) {
            StatusCount item = new StatusCount();
            item.setStatus(status);
            item.setCount(count);
            return item;
        }
    }
}
