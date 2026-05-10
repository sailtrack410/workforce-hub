package com.workforcehub.agent.dto;

import lombok.Data;

/**
 * 后台手动触发 Agent 人工确认超时扫描响应。
 */
@Data
public class AgentTaskTimeoutScanResponse {

    private Long cancelledCount;

    public static AgentTaskTimeoutScanResponse of(long cancelledCount) {
        AgentTaskTimeoutScanResponse response = new AgentTaskTimeoutScanResponse();
        response.setCancelledCount(cancelledCount);
        return response;
    }
}
