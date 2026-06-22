package com.workforcehub.agent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.repository.AgentRepository;
import com.workforcehub.agent.repository.AgentRow;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.spi.WorkflowAgentPreReviewTrigger;
import com.workforcehub.spi.WorkflowAgentResultCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/**
 * 生产环境流程预审 Agent 触发器。
 */
@Service
public class WorkflowAgentPreReviewTriggerService implements WorkflowAgentPreReviewTrigger {

    private static final String WF_REQUEST_CONTEXT_TYPE = "WF_REQUEST";

    private final AgentRepository agentRepository;
    private final ObjectProvider<WorkflowAgentResultCallback> workflowCallbackProvider;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public WorkflowAgentPreReviewTriggerService(AgentRepository agentRepository,
                                                ObjectProvider<WorkflowAgentResultCallback> workflowCallbackProvider,
                                                PlatformTransactionManager transactionManager,
                                                ObjectMapper objectMapper) {
        this.agentRepository = agentRepository;
        this.workflowCallbackProvider = workflowCallbackProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    @Override
    public Long triggerPreReview(Long requestId,
                                 String processTypeCode,
                                 String bizType,
                                 Long bizId,
                                 Long applicantUserId) {
        WorkflowAgentResultCallback callback = workflowCallbackProvider.getIfAvailable();
        AgentRow agent = agentRepository.findActivePreReviewAgent();
        if (callback == null || agent == null || agent.publishedVersionId() == null) {
            return null;
        }
        Long taskId = IdWorker.getId();
        agentRepository.insertTask(
            taskId,
            agent.id(),
            agent.publishedVersionId(),
            "WORKFLOW",
            WF_REQUEST_CONTEXT_TYPE,
            requestId,
            "WORKFLOW",
            applicantUserId,
            "PENDING",
            null,
            applicantUserId
        );
        agentRepository.insertAuditLog(
            IdWorker.getId(),
            taskId,
            "TASK_CREATED",
            "AGENT",
            agent.id(),
            "审批流程触发Agent预审",
            false
        );
        Runnable afterCommit = () -> completePreReview(taskId, requestId, processTypeCode, bizType, bizId, applicantUserId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    afterCommit.run();
                }
            });
        } else {
            afterCommit.run();
        }
        return taskId;
    }

    private void completePreReview(Long taskId,
                                   Long requestId,
                                   String processTypeCode,
                                   String bizType,
                                   Long bizId,
                                   Long applicantUserId) {
        String decisionJson = decisionJson(requestId, processTypeCode, bizType, bizId, applicantUserId);
        transactionTemplate.executeWithoutResult(status -> {
            agentRepository.markTaskRunning(taskId, applicantUserId);
            agentRepository.insertAuditLog(
                IdWorker.getId(),
                taskId,
                "TASK_RUNNING",
                "AGENT_TASK",
                taskId,
                "Agent预审开始执行",
                false
            );
            agentRepository.finishTask(taskId, "SUCCEEDED", null, applicantUserId);
            agentRepository.insertAuditLog(
                IdWorker.getId(),
                taskId,
                "TASK_SUCCEEDED",
                "AGENT_TASK",
                taskId,
                "Agent预审通过",
                false
            );
        });
        WorkflowAgentResultCallback callback = workflowCallbackProvider.getIfAvailable();
        if (callback != null) {
            callback.onPreReviewApproved(requestId, taskId, decisionJson);
        }
    }

    private String decisionJson(Long requestId,
                                String processTypeCode,
                                String bizType,
                                Long bizId,
                                Long applicantUserId) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "decision", "APPROVED",
                "reason", "Agent预审通过",
                "requestId", requestId,
                "processTypeCode", processTypeCode,
                "bizType", bizType,
                "bizId", bizId,
                "applicantUserId", applicantUserId
            ));
        } catch (Exception e) {
            throw new BizException(500, "Agent预审结果序列化失败");
        }
    }
}
