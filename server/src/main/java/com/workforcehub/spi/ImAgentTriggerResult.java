package com.workforcehub.spi;

/**
 * IM 会话 Agent 触发结果。
 */
public record ImAgentTriggerResult(
    Long agentTaskId,
    String status,
    String idempotencyKey
) {
}
