package com.workforcehub.agent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.dto.AgentTaskResponse;
import com.workforcehub.agent.model.AgentModelClient;
import com.workforcehub.agent.model.AgentModelException;
import com.workforcehub.agent.model.AgentModelRequest;
import com.workforcehub.agent.model.AgentModelResponse;
import com.workforcehub.agent.repository.AgentRepository;
import com.workforcehub.agent.repository.AgentRow;
import com.workforcehub.agent.repository.AgentTaskRow;
import com.workforcehub.agent.repository.AgentVersionRow;
import com.workforcehub.agent.workflow.WorkflowExecutionResult;
import com.workforcehub.agent.workflow.WorkflowExecutor;
import com.workforcehub.agent.workflow.WorkflowParser;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.spi.ImAgentReplyResult;
import com.workforcehub.spi.ImAgentResultCallback;
import com.workforcehub.spi.ImAgentTriggerCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Agent 任务执行服务。
 */
@Service
public class AgentTaskExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskExecutionService.class);

    private final AgentRepository agentRepository;
    private final ObjectMapper objectMapper;
    private final AgentModelClient agentModelClient;
    private final ObjectProvider<ImAgentResultCallback> imAgentResultCallbackProvider;
    private final Executor agentExecutor;
    private final TransactionTemplate transactionTemplate;
    private final WorkflowParser workflowParser;
    private final WorkflowExecutor workflowExecutor;

    public AgentTaskExecutionService(AgentRepository agentRepository,
                                     ObjectMapper objectMapper,
                                     AgentModelClient agentModelClient,
                                     ObjectProvider<ImAgentResultCallback> imAgentResultCallbackProvider,
                                     @Qualifier("agentExecutor") Executor agentExecutor,
                                     PlatformTransactionManager transactionManager,
                                     WorkflowParser workflowParser,
                                     WorkflowExecutor workflowExecutor) {
        this.agentRepository = agentRepository;
        this.objectMapper = objectMapper;
        this.agentModelClient = agentModelClient;
        this.imAgentResultCallbackProvider = imAgentResultCallbackProvider;
        this.agentExecutor = agentExecutor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.workflowParser = workflowParser;
        this.workflowExecutor = workflowExecutor;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AgentTaskResponse createConversationReplyTask(ImAgentTriggerCommand command) {
        Long conversationId = command.conversationId();
        Long agentId = command.agentId();
        Long senderId = command.triggerUserId();
        AgentRow agent = agentRepository.getAgentOrThrow(agentId);
        if (agent.publishedVersionId() == null || !"ACTIVE".equals(agent.status())) {
            throw new BizException(409, "Agent未发布或未启用");
        }
        if (!agentRepository.canUseAgent(agentId, senderId)) {
            throw new BizException(403, "无权使用该Agent");
        }
        String normalizedMessageContent = normalizeJson(command.messageContentJson(), "会话Agent输入内容不是合法JSON");
        Long taskId = IdWorker.getId();
        agentRepository.insertTask(
            taskId,
            agent.id(),
            agent.publishedVersionId(),
            "CONVERSATION",
            "IM_CONVERSATION",
            conversationId,
            "USER",
            senderId,
            "PENDING",
            null,
            senderId
        );
        audit(taskId, "TASK_CREATED", "AGENT", agent.id(), "IM会话触发Agent任务", false);
        scheduleConversationExecution(taskId, command);
        return toTaskResponse(agentRepository.getTaskOrThrow(taskId));
    }

    private void scheduleConversationExecution(Long taskId, ImAgentTriggerCommand command) {
        Runnable execution = () -> agentExecutor.execute(() -> executeConversationReplyTask(taskId, command));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    execution.run();
                }
            });
            return;
        }
        execution.run();
    }

    private void executeConversationReplyTask(Long taskId, ImAgentTriggerCommand command) {
        ConversationExecutionContext context = startConversationExecution(taskId, command);
        if (context == null) {
            return;
        }
        try {
            if (workflowParser.isWorkflowConfig(context.version().workflowConfigJson())) {
                WorkflowExecutionResult result = workflowExecutor.execute(
                    taskId,
                    context.agent().id(),
                    context.agent().agentName(),
                    context.version(),
                    context.messageContent(),
                    command.triggerUserId()
                );
                finishConversationSucceeded(command, context, result.replyText(), result.traceJson());
                return;
            }
            AgentModelResponse modelResponse = agentModelClient.generateReply(new AgentModelRequest(
                context.agent().id(),
                taskId,
                context.agent().agentName(),
                context.version().systemPrompt(),
                extractTextFromMessageContent(context.messageContent()),
                context.version().modelConfigJson()
            ));
            finishConversationSucceeded(command, context, modelResponse.text(), null);
        } catch (AgentModelException e) {
            finishConversationFailed(command, context, e.failureType(), e.getMessage());
        } catch (Exception e) {
            finishConversationFailed(command, context, "UNKNOWN", e.getMessage());
        }
    }

    private ConversationExecutionContext startConversationExecution(Long taskId, ImAgentTriggerCommand command) {
        return transactionTemplate.execute(status -> {
            AgentTaskRow task = agentRepository.getTaskOrThrow(taskId);
            if (!"PENDING".equals(task.status())) {
                return null;
            }
            AgentRow agent = agentRepository.getAgentOrThrow(task.agentId());
            AgentVersionRow version = agentRepository.getVersionOrThrow(task.agentVersionId());
            agentRepository.markTaskRunning(taskId, command.triggerUserId());
            if (workflowParser.isWorkflowConfig(version.workflowConfigJson())) {
                agentRepository.updateTaskWorkflowSnapshot(taskId, version.workflowConfigJson(), command.triggerUserId());
            }
            audit(taskId, "TASK_RUNNING", "AGENT_TASK", taskId, "Agent会话任务开始执行", false);
            return new ConversationExecutionContext(
                taskId,
                agent,
                version,
                normalizeJson(command.messageContentJson(), "会话Agent输入内容不是合法JSON")
            );
        });
    }

    private void finishConversationSucceeded(ImAgentTriggerCommand command,
                                             ConversationExecutionContext context,
                                             String replyText,
                                             String traceJson) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (StringUtils.hasText(traceJson)) {
                    agentRepository.updateTaskExecutionTrace(context.taskId(), traceJson, command.triggerUserId());
                }
                agentRepository.finishTask(context.taskId(), "SUCCEEDED", null, command.triggerUserId());
                audit(context.taskId(), "TASK_SUCCEEDED", "AGENT_TASK",
                    context.taskId(), "Agent会话任务执行成功", false);
                notifyImAgentSucceeded(command, context.agent(), context.taskId(), replyText);
            });
        } catch (Exception e) {
            finishConversationFailed(command, context, "UNKNOWN", e.getMessage());
        }
    }

    private void finishConversationFailed(ImAgentTriggerCommand command,
                                          ConversationExecutionContext context,
                                          String failureType,
                                          String failureMessage) {
        Long taskId = context.taskId();
        String safeFailureType = StringUtils.hasText(failureType) ? failureType : "UNKNOWN";
        String errorMessage = safeSummary(failureMessage);
        transactionTemplate.executeWithoutResult(status -> {
            agentRepository.finishTask(taskId, "FAILED", errorMessage, command.triggerUserId());
            audit(taskId, "TASK_FAILED", "AGENT_TASK", taskId, errorMessage, true);
        });
        try {
            notifyImAgentFailed(command, context.agent(), taskId, safeFailureType, errorMessage);
        } catch (Exception e) {
            log.warn("Agent会话失败结果回写IM失败 taskId={} conversationId={}",
                taskId,
                command.conversationId(),
                e
            );
        }
    }

    private void notifyImAgentSucceeded(ImAgentTriggerCommand command,
                                        AgentRow agent,
                                        Long taskId,
                                        String replyText) {
        ImAgentResultCallback callback = imAgentResultCallbackProvider.getIfAvailable();
        if (callback == null) {
            throw new BizException(501, "IM Agent结果回写服务未接入");
        }
        AgentConversationReplyContent replyContent = buildConversationReplyContent(replyText, taskId, agent.agentCode());
        callback.onAgentReply(new ImAgentReplyResult(
            command.conversationId(),
            command.sourceMessageId(),
            agent.id(),
            taskId,
            replyContent.messageType(),
            replyContent.contentJson(),
            "SUCCEEDED",
            null,
            null,
            agentMessageIdempotencyKey(taskId)
        ));
    }

    private AgentConversationReplyContent buildConversationReplyContent(String replyText, Long taskId, String agentCode) {
        String safeText = safeReplyText(replyText);
        JsonNode cardNode = parseAgentCardNode(safeText);
        if (cardNode instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
            objectNode.put("agentTaskId", taskId);
            objectNode.put("agentCode", agentCode);
            return new AgentConversationReplyContent("CARD", writeJson(objectNode));
        }
        return new AgentConversationReplyContent("TEXT", writeJson(Map.of(
            "text", safeText,
            "agentTaskId", taskId,
            "agentCode", agentCode
        )));
    }

    private JsonNode parseAgentCardNode(String replyText) {
        try {
            JsonNode node = objectMapper.readTree(replyText);
            JsonNode cardType = node.get("cardType");
            if (cardType == null) {
                return null;
            }
            String value = cardType.asText();
            return "knowledge_answer".equals(value) || "agent_result".equals(value) ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void notifyImAgentFailed(ImAgentTriggerCommand command,
                                     AgentRow agent,
                                     Long taskId,
                                     String failureType,
                                     String failureMessage) {
        ImAgentResultCallback callback = imAgentResultCallbackProvider.getIfAvailable();
        if (callback == null) {
            throw new BizException(501, "IM Agent结果回写服务未接入");
        }
        callback.onAgentReply(new ImAgentReplyResult(
            command.conversationId(),
            command.sourceMessageId(),
            agent.id(),
            taskId,
            null,
            null,
            "FAILED",
            failureType,
            failureMessage,
            agentMessageIdempotencyKey(taskId)
        ));
    }

    private String agentMessageIdempotencyKey(Long taskId) {
        return taskId == null ? null : "agent-task-" + taskId;
    }

    private String safeReplyText(String replyText) {
        if (!StringUtils.hasText(replyText)) {
            throw new AgentModelException("INVALID_RESPONSE", "模型服务返回内容为空");
        }
        return replyText.trim();
    }

    private String extractTextFromMessageContent(String messageContent) {
        try {
            JsonNode node = objectMapper.readTree(messageContent);
            JsonNode textNode = node.get("text");
            return textNode == null || textNode.isNull() ? "" : safeSummary(textNode.asText(""));
        } catch (Exception e) {
            return "";
        }
    }

    private void audit(Long taskId,
                       String actionType,
                       String targetType,
                       Long targetId,
                       String decisionSummary,
                       boolean riskFlag) {
        agentRepository.insertAuditLog(
            IdWorker.getId(),
            taskId,
            actionType,
            targetType,
            targetId,
            safeSummary(decisionSummary),
            riskFlag
        );
    }

    private String normalizeJson(String value, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(value));
        } catch (Exception e) {
            throw new BizException(400, errorMessage);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(500, "JSON序列化失败");
        }
    }

    private String safeSummary(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 512 ? trimmed.substring(0, 512) : trimmed;
    }

    private AgentTaskResponse toTaskResponse(AgentTaskRow row) {
        AgentTaskResponse response = new AgentTaskResponse();
        response.setTaskId(row.id());
        response.setAgentId(row.agentId());
        response.setAgentVersionId(row.agentVersionId());
        response.setTriggerType(row.triggerType());
        response.setContextType(row.contextType());
        response.setContextId(row.contextId());
        response.setStatus(row.status());
        response.setToolCallCount(agentRepository.countToolCalls(row.id()));
        response.setWaitingDeadlineAt(row.waitingDeadlineAt());
        response.setStartedAt(row.startedAt());
        response.setFinishedAt(row.finishedAt());
        response.setErrorMessage(row.errorMessage());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
    }

    private record ConversationExecutionContext(
        Long taskId,
        AgentRow agent,
        AgentVersionRow version,
        String messageContent
    ) {
    }

    private record AgentConversationReplyContent(
        String messageType,
        String contentJson
    ) {
    }
}
