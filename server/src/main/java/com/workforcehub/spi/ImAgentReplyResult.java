package com.workforcehub.spi;

/**
 * Agent 会话回复回写 IM 的跨模块结果。
 */
public record ImAgentReplyResult(
    Long conversationId,
    Long sourceMessageId,
    Long agentId,
    Long agentTaskId,
    String messageType,
    String contentJson,
    String status,
    String failureType,
    String failureMessage,
    String idempotencyKey
) {
}
