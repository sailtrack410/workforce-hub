package com.workforcehub.im.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 后台 IM outbox 派发请求。
 */
@Data
public class AdminImOutboxDispatchRequest {

    @Min(value = 1, message = "batchSize 最小为 1")
    @Max(value = 500, message = "batchSize 最大为 500")
    private Integer batchSize;

    public int safeBatchSize() {
        return batchSize == null ? 50 : batchSize;
    }
}
