package com.workforcehub.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 后台手动触发 Agent 人工确认超时扫描请求。
 */
@Data
public class AgentTaskTimeoutScanRequest {

    private static final int DEFAULT_BATCH_SIZE = 100;

    @Min(value = 1, message = "超时扫描批次大小必须大于0")
    @Max(value = 500, message = "超时扫描批次大小不能超过500")
    private Integer batchSize = DEFAULT_BATCH_SIZE;

    public int safeBatchSize() {
        return batchSize == null ? DEFAULT_BATCH_SIZE : batchSize;
    }
}
