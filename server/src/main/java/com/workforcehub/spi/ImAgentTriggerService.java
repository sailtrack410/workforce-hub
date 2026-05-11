package com.workforcehub.spi;

/**
 * IM 会话 Agent 触发接口
 * 由 module-agent 实现，module-im 调用
 */
public interface ImAgentTriggerService {

    /**
     * 校验当前员工是否可在 IM 中使用指定 Agent，并返回会话展示元信息。
     *
     * @param agentId Agent ID
     * @param userId 员工用户 ID
     * @return Agent 元信息
     */
    ImAgentDescriptor requireUsableAgent(Long agentId, Long userId);

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
