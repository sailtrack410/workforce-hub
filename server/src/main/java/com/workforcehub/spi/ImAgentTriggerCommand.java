package com.workforcehub.spi;

/**
 * IM 会话触发 Agent 回复的跨模块命令。
 */
public record ImAgentTriggerCommand(
    Long conversationId,
    Long sourceMessageId,
    Long agentId,
    Long triggerUserId,
    String triggerSenderType,
    String messageContentJson,
    String clientRequestId,
    String idempotencyKey,
    String metadataJson
) {
}
