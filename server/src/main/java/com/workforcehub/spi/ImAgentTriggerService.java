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
     * 在会话中触发 Agent 回复。
     *
     * @param command 触发命令
     * @return Agent 任务触发结果
     */
    ImAgentTriggerResult triggerConversationReply(ImAgentTriggerCommand command);
}
