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
        private String beforeStatusName;
        private String afterStatus;
        private String afterStatusName;
        private String skipReason;

        public static Item succeeded(Long eventId, String beforeStatus, String afterStatus) {
            Item item = new Item();
            item.setEventId(eventId);
            item.setSucceeded(true);
            item.setBeforeStatus(beforeStatus);
            item.setBeforeStatusName(statusName(beforeStatus));
            item.setAfterStatus(afterStatus);
            item.setAfterStatusName(statusName(afterStatus));
            return item;
        }

        public static Item skipped(Long eventId, String beforeStatus, String skipReason) {
            Item item = new Item();
            item.setEventId(eventId);
            item.setSucceeded(false);
            item.setBeforeStatus(beforeStatus);
            item.setBeforeStatusName(statusName(beforeStatus));
            item.setSkipReason(skipReason);
            return item;
        }

        private static String statusName(String value) {
            if ("PENDING".equals(value)) return "待发送";
            if ("SENDING".equals(value)) return "发送中";
            if ("SENT".equals(value)) return "已发送";
            if ("FAILED".equals(value)) return "失败";
            if ("DEAD".equals(value)) return "终止";
            return value == null ? null : "其他状态";
        }
    }
}
