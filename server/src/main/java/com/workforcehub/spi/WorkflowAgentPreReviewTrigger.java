package com.workforcehub.spi;

/**
 * 流程预审 Agent 触发接口
 * 由 module-agent 实现，module-workflow 调用
 */
public interface WorkflowAgentPreReviewTrigger {

    /**
     * 触发 Agent 预审任务
     *
     * @param requestId 申请单ID
     * @param processTypeCode 流程类型编码
     * @param bizType 业务类型
     * @param bizId 业务明细ID
     * @param applicantUserId 申请人ID
     * @return Agent 任务ID，如果无需预审返回 null
     */
    Long triggerPreReview(Long requestId, String processTypeCode, String bizType, Long bizId, Long applicantUserId);
}
