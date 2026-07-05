package com.workforcehub.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workforcehub.agent.model.AgentModelRequest;
import com.workforcehub.agent.model.AgentModelResponse;
import com.workforcehub.agent.model.OpenAiCompatibleAgentModelClient;
import com.workforcehub.agent.repository.AgentRepository;
import com.workforcehub.agent.repository.AgentRow;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.ragflow.repository.RagflowGlobalSettingRow;
import com.workforcehub.ragflow.repository.RagflowRepository;
import com.workforcehub.workflow.repository.WorkflowLeaveDetailRow;
import com.workforcehub.workflow.repository.WorkflowRepository;
import com.workforcehub.workflow.repository.WorkflowRequestRow;
import com.workforcehub.workflow.service.WorkflowAgentPreReviewTrigger;
import com.workforcehub.workflow.service.WorkflowAgentResultCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 生产环境流程预审 Agent 触发器。
 */
// 流程预审：查找匹配的 Agent 配置，构建 prompt 调用 LLM，事务提交后异步执行
@Service
public class WorkflowAgentPreReviewTriggerService implements WorkflowAgentPreReviewTrigger {

    private static final String WF_REQUEST_CONTEXT_TYPE = "WF_REQUEST";

    private final AgentRepository agentRepository;
    private final WorkflowRepository workflowRepository;
    private final RagflowRepository ragflowRepository;
    private final OpenAiCompatibleAgentModelClient agentModelClient;
    private final ObjectProvider<WorkflowAgentResultCallback> workflowCallbackProvider;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public WorkflowAgentPreReviewTriggerService(AgentRepository agentRepository,
                                                WorkflowRepository workflowRepository,
                                                RagflowRepository ragflowRepository,
                                                OpenAiCompatibleAgentModelClient agentModelClient,
                                                ObjectProvider<WorkflowAgentResultCallback> workflowCallbackProvider,
                                                PlatformTransactionManager transactionManager,
                                                ObjectMapper objectMapper) {
        this.agentRepository = agentRepository;
        this.workflowRepository = workflowRepository;
        this.ragflowRepository = ragflowRepository;
        this.agentModelClient = agentModelClient;
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
        Long taskId = IdGenerator.nextId();
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
            IdGenerator.nextId(),
            taskId,
            "TASK_CREATED",
            "AGENT",
            agent.id(),
            "审批流程触发AI预审",
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
        transactionTemplate.executeWithoutResult(status -> {
            agentRepository.markTaskRunning(taskId, applicantUserId);
            agentRepository.insertAuditLog(
                IdGenerator.nextId(),
                taskId,
                "TASK_RUNNING",
                "AGENT_TASK",
                taskId,
                "AI预审开始执行",
                false
            );
        });

        try {
            String decisionJson = decisionJson(taskId, requestId, processTypeCode, bizType, bizId, applicantUserId);
            transactionTemplate.executeWithoutResult(status -> {
                agentRepository.finishTask(taskId, "SUCCEEDED", null, applicantUserId);
                agentRepository.insertAuditLog(
                    IdGenerator.nextId(),
                    taskId,
                    "TASK_SUCCEEDED",
                    "AGENT_TASK",
                    taskId,
                    "AI预审完成",
                    false
                );
            });
            notifyWorkflow(requestId, taskId, decisionJson);
        } catch (Exception e) {
            String decisionJson = failureDecisionJson(requestId, processTypeCode, bizType, bizId, applicantUserId, e);
            transactionTemplate.executeWithoutResult(status -> {
                agentRepository.finishTask(taskId, "FAILED", compactMessage(e), applicantUserId);
                agentRepository.insertAuditLog(
                    IdGenerator.nextId(),
                    taskId,
                    "TASK_FAILED",
                    "AGENT_TASK",
                    taskId,
                    compactMessage(e),
                    true
                );
            });
            WorkflowAgentResultCallback callback = workflowCallbackProvider.getIfAvailable();
            if (callback != null) {
                callback.onPreReviewToHuman(requestId, taskId, decisionJson);
            }
        }
    }

    private String decisionJson(Long taskId,
                                Long requestId,
                                String processTypeCode,
                                String bizType,
                                Long bizId,
                                Long applicantUserId) {
        try {
            WorkflowRequestRow request = workflowRepository.getRequestByIdOrThrow(requestId);
            WorkflowLeaveDetailRow leave = workflowRepository.getLeaveDetailByRequestId(requestId);
            AgentModelResponse response = agentModelClient.generateReply(new AgentModelRequest(
                null,
                taskId,
                "OA预审",
                preReviewSystemPrompt(),
                preReviewInputJson(request, leave, processTypeCode, bizType, bizId, applicantUserId),
                ragflowModelConfigJson()
            ));
            return normalizeDecisionJson(response.text(), requestId, processTypeCode, bizType, bizId, applicantUserId);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, StringUtils.hasText(e.getMessage()) ? e.getMessage() : "AI预审调用失败");
        }
    }

    private String ragflowModelConfigJson() throws Exception {
        RagflowGlobalSettingRow setting = ragflowRepository.getGlobalSetting();
        if (!StringUtils.hasText(setting.ocrBaseUrl()) || !StringUtils.hasText(setting.ocrModel())
            || !Boolean.TRUE.equals(setting.ocrApiKeyConfigured())) {
            throw new BizException(422, "知识库OCR模型未配置");
        }
        String apiKey = ragflowRepository.getRawOcrApiKey();
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException(422, "知识库OCR模型未配置");
        }
        return objectMapper.writeValueAsString(Map.of(
            "baseUrl", setting.ocrBaseUrl(),
            "apiKey", apiKey,
            "model", setting.ocrModel(),
            "temperature", 0.1,
            "maxTokens", 1024
        ));
    }

    private String preReviewSystemPrompt() {
        return """
            你是OA请假申请预审Agent。只输出一个JSON对象，不要输出Markdown。
            JSON字段:
            decision: APPROVED、REJECTED、TO_HUMAN 三选一。
            conclusion: 一句话结论。
            reason: 预审依据。
            suggestion: 给直属上级的审批建议。
            riskLevel: 低、中、高。
            checks: 数组，每项包含 name 和 result。
            只有明显缺少核心信息或明显违规时才能 REJECTED；不确定时输出 TO_HUMAN。
            """;
    }

    private String preReviewInputJson(WorkflowRequestRow request,
                                      WorkflowLeaveDetailRow leave,
                                      String processTypeCode,
                                      String bizType,
                                      Long bizId,
                                      Long applicantUserId) throws Exception {
        ObjectNode input = objectMapper.createObjectNode();
        input.put("requestId", request.id());
        input.put("requestNo", request.requestNo());
        input.put("title", request.title());
        input.put("processTypeCode", processTypeCode);
        input.put("bizType", bizType);
        input.put("bizId", bizId);
        input.put("applicantUserId", applicantUserId);
        if (leave != null) {
            ObjectNode leaveNode = input.putObject("leave");
            leaveNode.put("leaveTypeId", leave.leaveTypeId());
            leaveNode.put("startAt", String.valueOf(leave.startAt()));
            leaveNode.put("endAt", String.valueOf(leave.endAt()));
            leaveNode.put("durationMinutes", leave.durationMinutes());
            leaveNode.put("reason", leave.reason());
        }
        return objectMapper.writeValueAsString(input);
    }

    private String normalizeDecisionJson(String modelText,
                                         Long requestId,
                                         String processTypeCode,
                                         String bizType,
                                         Long bizId,
                                         Long applicantUserId) throws Exception {
        JsonNode node = objectMapper.readTree(extractJsonObject(modelText));
        ObjectNode decision = objectMapper.createObjectNode();
        String decisionValue = normalizeDecision(node.path("decision").asText());
        decision.put("decision", decisionValue);
        decision.put("conclusion", textOrDefault(node, "conclusion", defaultConclusion(decisionValue)));
        decision.put("reason", textOrDefault(node, "reason", "模型已完成预审"));
        decision.put("suggestion", textOrDefault(node, "suggestion", "建议直属上级结合业务安排复核后审批"));
        decision.put("riskLevel", textOrDefault(node, "riskLevel", "中"));
        decision.put("reviewMode", "知识库模型预审");
        JsonNode checks = node.path("checks");
        decision.set("checks", checks.isArray() ? checks : objectMapper.createArrayNode());
        appendContext(decision, requestId, processTypeCode, bizType, bizId, applicantUserId);
        return objectMapper.writeValueAsString(decision);
    }

    private String extractJsonObject(String text) {
        if (!StringUtils.hasText(text)) {
            throw new BizException(502, "AI预审未返回内容");
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                trimmed = trimmed.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BizException(502, "AI预审返回不是JSON对象");
        }
        return trimmed.substring(start, end + 1);
    }

    private String normalizeDecision(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if ("APPROVED".equals(normalized) || "REJECTED".equals(normalized) || "TO_HUMAN".equals(normalized)) {
            return normalized;
        }
        return "TO_HUMAN";
    }

    private String textOrDefault(JsonNode node, String fieldName, String fallback) {
        String value = node.path(fieldName).asText("");
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String defaultConclusion(String decision) {
        if ("APPROVED".equals(decision)) {
            return "建议进入直属上级审批";
        }
        if ("REJECTED".equals(decision)) {
            return "建议驳回申请";
        }
        return "建议转人工复核";
    }

    private void appendContext(ObjectNode decision,
                               Long requestId,
                               String processTypeCode,
                               String bizType,
                               Long bizId,
                               Long applicantUserId) {
        decision.put("requestId", requestId);
        decision.put("processTypeCode", processTypeCode);
        decision.put("bizType", bizType);
        decision.put("bizId", bizId);
        decision.put("applicantUserId", applicantUserId);
    }

    private String failureDecisionJson(Long requestId,
                                       String processTypeCode,
                                       String bizType,
                                       Long bizId,
                                       Long applicantUserId,
                                       Exception error) {
        try {
            ObjectNode decision = objectMapper.createObjectNode();
            decision.put("decision", "TO_HUMAN");
            decision.put("conclusion", "AI预审未完成，转人工复核");
            decision.put("reason", compactMessage(error));
            decision.put("suggestion", "建议直属上级人工核对申请信息后审批");
            decision.put("riskLevel", "中");
            decision.put("reviewMode", "知识库模型预审");
            ArrayNode checks = decision.putArray("checks");
            ObjectNode check = checks.addObject();
            check.put("name", "模型调用");
            check.put("result", compactMessage(error));
            appendContext(decision, requestId, processTypeCode, bizType, bizId, applicantUserId);
            return objectMapper.writeValueAsString(decision);
        } catch (Exception e) {
            throw new BizException(500, "AI预审结果序列化失败");
        }
    }

    private void notifyWorkflow(Long requestId, Long taskId, String decisionJson) {
        WorkflowAgentResultCallback callback = workflowCallbackProvider.getIfAvailable();
        if (callback == null) {
            return;
        }
        try {
            String decision = objectMapper.readTree(decisionJson).path("decision").asText("TO_HUMAN");
            if ("REJECTED".equals(decision)) {
                callback.onPreReviewRejected(requestId, taskId, decisionJson);
            } else if ("APPROVED".equals(decision)) {
                callback.onPreReviewApproved(requestId, taskId, decisionJson);
            } else {
                callback.onPreReviewToHuman(requestId, taskId, decisionJson);
            }
        } catch (Exception e) {
            callback.onPreReviewToHuman(requestId, taskId, decisionJson);
        }
    }

    private String compactMessage(Exception e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            message = "AI预审调用失败";
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}
