package com.workforcehub.im.dto;

import lombok.Data;

import java.util.List;

/**
 * 后台 IM outbox 批量重试响应。
 */
@Data
public class AdminImOutboxBatchRetryResponse {

    private int requestedCount;
    private int succeededCount;
    private int skippedCount;
    private List<Item> items;

    public static AdminImOutboxBatchRetryResponse of(List<Item> items) {
        AdminImOutboxBatchRetryResponse response = new AdminImOutboxBatchRetryResponse();
        response.setItems(items);
        response.setRequestedCount(items.size());
        response.setSucceededCount((int) items.stream().filter(Item::isSucceeded).count());
        response.setSkippedCount((int) items.stream().filter(item -> !item.isSucceeded()).count());
        return response;
    }

    @Data
    public static class Item {
        private Long eventId;
        private boolean succeeded;
        private String beforeStatus;
        private String afterStatus;
        private String skipReason;

        public static Item succeeded(Long eventId, String beforeStatus, String afterStatus) {
            Item item = new Item();
            item.setEventId(eventId);
            item.setSucceeded(true);
            item.setBeforeStatus(beforeStatus);
            item.setAfterStatus(afterStatus);
            return item;
        }

        public static Item skipped(Long eventId, String beforeStatus, String skipReason) {
            Item item = new Item();
            item.setEventId(eventId);
            item.setSucceeded(false);
            item.setBeforeStatus(beforeStatus);
            item.setSkipReason(skipReason);
            return item;
        }
    }
}
