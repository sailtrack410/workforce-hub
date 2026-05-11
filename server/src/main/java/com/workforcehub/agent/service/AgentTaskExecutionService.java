package com.workforcehub.agent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.dto.AgentTaskResponse;
import com.workforcehub.agent.repository.AgentRepository;
import com.workforcehub.agent.repository.AgentRow;
import com.workforcehub.agent.repository.AgentTaskRow;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.spi.ImAgentReplyResult;
import com.workforcehub.spi.ImAgentResultCallback;
import com.workforcehub.spi.ImAgentTriggerCommand;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Agent 任务执行服务。
 */
@Service
public class AgentTaskExecutionService {

    private final AgentRepository agentRepository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ImAgentResultCallback> imAgentResultCallbackProvider;

    public AgentTaskExecutionService(AgentRepository agentRepository,
                                     ObjectMapper objectMapper,
                                     ObjectProvider<ImAgentResultCallback> imAgentResultCallbackProvider) {
        this.agentRepository = agentRepository;
        this.objectMapper = objectMapper;
        this.imAgentResultCallbackProvider = imAgentResultCallbackProvider;
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
        agentRepository.markTaskRunning(taskId, senderId);
        audit(taskId, "TASK_RUNNING", "AGENT_TASK", taskId, "Agent会话任务开始执行", false);
        try {
            // 第一版会话 Agent 不接外部模型，先产出可演示、可审计的同步回复。
            agentRepository.finishTask(taskId, "SUCCEEDED", null, senderId);
            audit(taskId, "TASK_SUCCEEDED", "AGENT_TASK", taskId, "Agent会话任务执行成功", false);
            notifyImAgentSucceeded(command, agent, taskId, normalizedMessageContent);
        } catch (BizException e) {
            String errorMessage = safeSummary(e.getMessage());
            agentRepository.finishTask(taskId, "FAILED", errorMessage, senderId);
            audit(taskId, "TASK_FAILED", "AGENT_TASK", taskId, errorMessage, true);
        }
        return toTaskResponse(agentRepository.getTaskOrThrow(taskId));
    }

    private void notifyImAgentSucceeded(ImAgentTriggerCommand command,
                                        AgentRow agent,
                                        Long taskId,
                                        String messageContent) {
        ImAgentResultCallback callback = imAgentResultCallbackProvider.getIfAvailable();
        if (callback == null) {
            throw new BizException(501, "IM Agent结果回写服务未接入");
        }
        callback.onAgentReply(new ImAgentReplyResult(
            command.conversationId(),
            command.sourceMessageId(),
            agent.id(),
            taskId,
            "TEXT",
            writeJson(Map.of(
                "text", buildConversationReplyText(agent, messageContent),
                "agentTaskId", taskId,
                "agentCode", agent.agentCode()
            )),
            "SUCCEEDED",
            null,
            null,
            agentMessageIdempotencyKey(taskId)
        ));
    }

    private String agentMessageIdempotencyKey(Long taskId) {
        return taskId == null ? null : "agent-task-" + taskId;
    }

    private String buildConversationReplyText(AgentRow agent, String messageContent) {
        String text = extractTextFromMessageContent(messageContent);
        if (!StringUtils.hasText(text)) {
            return "%s 已收到你的消息，当前演示环境已完成会话任务闭环。".formatted(agent.agentName());
        }
        return "%s 已收到：%s".formatted(agent.agentName(), text);
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
}
