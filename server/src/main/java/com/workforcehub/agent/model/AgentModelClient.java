package com.workforcehub.agent.model;

/**
 * Agent 会话模型调用客户端。
 */
public interface AgentModelClient {

    AgentModelResponse generateReply(AgentModelRequest request);
}
