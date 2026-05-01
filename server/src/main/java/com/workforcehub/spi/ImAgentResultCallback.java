package com.workforcehub.spi;

/**
 * Agent 会话结果回调接口
 * 由 module-im 实现，module-agent 调用
 */
public interface ImAgentResultCallback {

    /**
     * Agent 回复消息到会话
     *
     * @param conversationId 会话ID
     * @param agentId Agent ID
     * @param agentTaskId Agent 任务ID
     * @param messageType 消息类型
     * @param contentJson 消息内容JSON
     */
    void sendAgentMessage(Long conversationId, Long agentId, Long agentTaskId, String messageType, String contentJson);
}
