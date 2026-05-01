package com.workforcehub.spi;

/**
 * IM 会话 Agent 触发接口
 * 由 module-agent 实现，module-im 调用
 */
public interface ImAgentTriggerService {

    /**
     * 在会话中触发 Agent 回复
     *
     * @param conversationId 会话ID
     * @param agentId Agent ID
     * @param senderId 发送人ID
     * @param senderType 发送人类型
     * @param messageContent 消息内容JSON
     * @return Agent 任务ID
     */
    Long triggerConversationReply(Long conversationId, Long agentId, Long senderId, String senderType, String messageContent);
}
