package com.workforcehub.spi;

/**
 * Agent 会话结果回调接口
 * 由 module-im 实现，module-agent 调用
 */
public interface ImAgentResultCallback {

    /**
     * Agent 回复消息到会话。
     *
     * @param result Agent 回复结果
     */
    void onAgentReply(ImAgentReplyResult result);
}
