package com.workforcehub.agent.service;

/**
 * IM 创建 Agent 会话所需的最小 Agent 元信息。
 */
public record ImAgentDescriptor(
    Long agentId,
    String agentCode,
    String agentName
) {
}
