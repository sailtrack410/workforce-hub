package com.workforcehub.agent.model;

/**
 * Agent 会话模型调用请求。
 */
public record AgentModelRequest(
    Long agentId,
    Long agentTaskId,
    String agentName,
    String systemPrompt,
    String userMessage,
    String modelConfigJson
) {
}
