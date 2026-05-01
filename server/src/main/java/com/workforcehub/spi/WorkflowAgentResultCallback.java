package com.workforcehub.spi;

/**
 * Agent 预审结果回调接口
 * 由 module-workflow 实现，module-agent 调用
 */
public interface WorkflowAgentResultCallback {

    /**
     * Agent 预审通过
     *
     * @param requestId 申请单ID
     * @param agentTaskId Agent任务ID
     * @param decisionJson 结构化预审结论JSON
     */
    void onPreReviewApproved(Long requestId, Long agentTaskId, String decisionJson);

    /**
     * Agent 预审驳回
     *
     * @param requestId 申请单ID
     * @param agentTaskId Agent任务ID
     * @param decisionJson 结构化预审结论JSON
     */
    void onPreReviewRejected(Long requestId, Long agentTaskId, String decisionJson);

    /**
     * Agent 预审转人工
     *
     * @param requestId 申请单ID
     * @param agentTaskId Agent任务ID
     * @param decisionJson 结构化预审结论JSON
     */
    void onPreReviewToHuman(Long requestId, Long agentTaskId, String decisionJson);
}
