package com.workforcehub.im.dto;

import lombok.Data;

/**
 * 后台 IM outbox 派发结果。
 */
@Data
public class AdminImOutboxDispatchResponse {

    private int requestedBatchSize;
    private int dispatchedCount;

    public static AdminImOutboxDispatchResponse of(int requestedBatchSize, int dispatchedCount) {
        AdminImOutboxDispatchResponse response = new AdminImOutboxDispatchResponse();
        response.setRequestedBatchSize(requestedBatchSize);
        response.setDispatchedCount(dispatchedCount);
        return response;
    }
}
