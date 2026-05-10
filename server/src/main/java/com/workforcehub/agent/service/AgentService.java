package com.workforcehub.agent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.dto.CancelAgentTaskRequest;
import com.workforcehub.agent.dto.ConfirmAgentTaskRequest;
import com.workforcehub.agent.dto.AgentAclResponse;
import com.workforcehub.agent.dto.AgentResponse;
import com.workforcehub.agent.dto.AgentTaskResponse;
import com.workforcehub.agent.dto.AgentToolBindingResponse;
import com.workforcehub.agent.dto.AgentToolCallRequest;
import com.workforcehub.agent.dto.AgentToolResponse;
import com.workforcehub.agent.dto.AgentVersionResponse;
import com.workforcehub.agent.dto.BindAgentToolRequest;
import com.workforcehub.agent.dto.CreateAgentRequest;
import com.workforcehub.agent.dto.CreateAgentTaskRequest;
import com.workforcehub.agent.dto.CreateAgentToolRequest;
import com.workforcehub.agent.dto.CreateAgentVersionRequest;
import com.workforcehub.agent.dto.GrantAgentAclRequest;
import com.workforcehub.agent.repository.AgentAclRow;
import com.workforcehub.agent.repository.AgentRepository;
import com.workforcehub.agent.repository.AgentRow;
import com.workforcehub.agent.repository.AgentPendingToolCallRow;
import com.workforcehub.agent.repository.AgentTaskRow;
import com.workforcehub.agent.repository.AgentToolBindingRow;
import com.workforcehub.agent.repository.AgentToolRow;
import com.workforcehub.agent.repository.AgentVersionRow;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * module-agent 第一批治理、授权、任务和工具调用服务。
 */
@Service
public class AgentService {

    private static final Set<String> AGENT_TYPES = Set.of("QA", "PRE_REVIEW", "REMINDER", "AUTOMATION");
    private static final Set<String> IDENTITY_MODES = Set.of("SYSTEM_IDENTITY", "USER_DELEGATED");
    private static final Set<String> TOOL_TYPES = Set.of("SPI", "CONNECTOR");
    private static final Set<String> RISK_LEVELS = Set.of("READONLY", "LOW", "MEDIUM", "HIGH");
    private static final Set<String> RECORD_STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> SUBJECT_TYPES = Set.of("USER", "DEPARTMENT", "GROUP", "ADMIN_USER");
    private static final Set<String> PERMISSIONS = Set.of("visible", "use", "manage");
    private static final Set<String> TRIGGER_TYPES = Set.of("MANUAL", "CONVERSATION", "WORKFLOW", "TIMER", "EVENT");
    private static final Set<String> APPROVAL_MODES = Set.of("AUTO", "REQUIRE_CONFIRM");

    private final AgentRepository agentRepository;
    private final ObjectMapper objectMapper;
    private final AgentToolExecutionService toolExecutionService;

    public AgentService(AgentRepository agentRepository,
                        ObjectMapper objectMapper,
                        AgentToolExecutionService toolExecutionService) {
        this.agentRepository = agentRepository;
        this.objectMapper = objectMapper;
        this.toolExecutionService = toolExecutionService;
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentResponse createAgent(CreateAgentRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        String agentCode = trimToRequired(request.getAgentCode(), "Agent编码不能为空").toUpperCase(Locale.ROOT);
        if (agentRepository.existsAgentCode(agentCode)) {
            throw new BizException(409, "Agent编码已存在");
        }
        Long agentId = IdWorker.getId();
        agentRepository.insertAgent(
            agentId,
            agentCode,
            trimToRequired(request.getAgentName(), "Agent名称不能为空"),
            normalizeIn(request.getAgentType(), AGENT_TYPES, "Agent类型不支持"),
            normalizeDefault(request.getIdentityMode(), "SYSTEM_IDENTITY", IDENTITY_MODES, "Agent身份模式不支持"),
            operatorId
        );
        return toAgentResponse(agentRepository.getAgentOrThrow(agentId));
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentVersionResponse createVersion(Long agentId, CreateAgentVersionRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        agentRepository.getAgentOrThrow(agentId);
        if (agentRepository.existsAgentVersion(agentId, request.getVersionNo())) {
            throw new BizException(409, "Agent版本号已存在");
        }
        Long versionId = IdWorker.getId();
        agentRepository.insertAgentVersion(
            versionId,
            agentId,
            request.getVersionNo(),
            normalizeJson(request.getModelConfigJson(), "模型配置不是合法JSON"),
            trimToNull(request.getSystemPrompt()),
            normalizeJson(request.getWorkflowConfigJson(), "工作流配置不是合法JSON"),
            operatorId
        );
        return toVersionResponse(agentRepository.getVersionOrThrow(versionId));
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentToolResponse createTool(CreateAgentToolRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        String toolCode = trimToRequired(request.getToolCode(), "工具编码不能为空").toUpperCase(Locale.ROOT);
        if (agentRepository.existsToolCode(toolCode)) {
            throw new BizException(409, "工具编码已存在");
        }
        String toolType = normalizeIn(request.getToolType(), TOOL_TYPES, "工具类型不支持");
        Long toolId = IdWorker.getId();
        agentRepository.insertTool(
            toolId,
            toolCode,
            trimToRequired(request.getToolName(), "工具名称不能为空"),
            toolType,
            request.getConnectorId(),
            normalizeIn(request.getRiskLevel(), RISK_LEVELS, "风险等级不支持"),
            Boolean.TRUE.equals(request.getReadOnlyFlag()),
            normalizeDefault(request.getStatus(), "ACTIVE", RECORD_STATUSES, "工具状态不支持"),
            operatorId
        );
        return toToolResponse(agentRepository.getToolOrThrow(toolId));
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentToolBindingResponse bindTool(Long versionId, BindAgentToolRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        AgentVersionRow version = agentRepository.getVersionOrThrow(versionId);
        if (!"DRAFT".equals(version.publishStatus())) {
            throw new BizException(409, "只有草稿版本可以绑定工具");
        }
        AgentToolRow tool = agentRepository.getToolOrThrow(request.getToolId());
        if (!"ACTIVE".equals(tool.status())) {
            throw new BizException(409, "工具未启用");
        }
        Long bindingId = IdWorker.getId();
        agentRepository.insertToolBinding(
            bindingId,
            versionId,
            request.getToolId(),
            normalizeDefault(request.getApprovalMode(), "AUTO", APPROVAL_MODES, "工具审批模式不支持"),
            normalizeJson(request.getArgSchemaJson(), "参数Schema不是合法JSON"),
            normalizeJson(request.getRiskControlJson(), "风控配置不是合法JSON"),
            normalizeDefault(request.getStatus(), "ACTIVE", RECORD_STATUSES, "工具绑定状态不支持"),
            operatorId
        );
        return toBindingResponse(agentRepository.getBindingOrThrow(bindingId));
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentAclResponse grantAcl(Long agentId, GrantAgentAclRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        agentRepository.getAgentOrThrow(agentId);
        String subjectType = normalizeIn(request.getSubjectType(), SUBJECT_TYPES, "授权主体类型不支持");
        String permCode = normalizePermission(request.getPermCode());
        agentRepository.upsertAcl(
            IdWorker.getId(),
            agentId,
            subjectType,
            request.getSubjectId(),
            permCode,
            normalizeDefault(request.getStatus(), "ACTIVE", RECORD_STATUSES, "授权状态不支持"),
            operatorId
        );
        return toAclResponse(agentRepository.getAclOrThrow(agentId, subjectType, request.getSubjectId(), permCode));
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentResponse publishVersion(Long versionId) {
        Long operatorId = CurrentUserHolder.requireUserId();
        AgentVersionRow version = agentRepository.getVersionOrThrow(versionId);
        if (!"DRAFT".equals(version.publishStatus())) {
            throw new BizException(409, "只有草稿版本可以发布");
        }
        agentRepository.getAgentOrThrow(version.agentId());
        agentRepository.publishVersion(version.agentId(), versionId, operatorId);
        return toAgentResponse(agentRepository.getAgentOrThrow(version.agentId()));
    }

    public List<AgentResponse> listCurrentEmployeeAgents() {
        Long userId = CurrentUserHolder.requireUserId();
        return agentRepository.listUsableAgentsForUser(userId).stream()
            .map(this::toAgentResponse)
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentTaskResponse createCurrentEmployeeTask(Long agentId, CreateAgentTaskRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        AgentRow agent = agentRepository.getAgentOrThrow(agentId);
        if (!agentRepository.canUseAgent(agentId, userId)) {
            throw new BizException(403, "无权使用该Agent");
        }
        if (agent.publishedVersionId() == null || !"ACTIVE".equals(agent.status())) {
            throw new BizException(409, "Agent未发布或未启用");
        }
        normalizeJson(request.getInputPayloadJson(), "输入载荷不是合法JSON");
        String triggerType = normalizeIn(request.getTriggerType(), TRIGGER_TYPES, "触发类型不支持");

        List<PreparedToolCall> preparedToolCalls = safeToolCalls(request).stream()
            .map(toolCall -> prepareToolCall(agent.publishedVersionId(), toolCall))
            .toList();

        boolean requireHumanConfirm = preparedToolCalls.stream()
            .anyMatch(call -> "REQUIRE_CONFIRM".equals(call.binding().approvalMode()));
        Long taskId = IdWorker.getId();
        String initialStatus = requireHumanConfirm ? "WAITING_HUMAN_CONFIRM" : "PENDING";
        LocalDateTime waitingDeadlineAt = requireHumanConfirm ? LocalDateTime.now().plusHours(24) : null;
        agentRepository.insertTask(
            taskId,
            agent.id(),
            agent.publishedVersionId(),
            triggerType,
            trimToNull(request.getContextType()),
            request.getContextId(),
            "USER",
            userId,
            initialStatus,
            waitingDeadlineAt,
            userId
        );
        audit(taskId, "TASK_CREATED", "AGENT", agent.id(), "员工创建Agent任务", false);

        if (requireHumanConfirm) {
            for (PreparedToolCall call : preparedToolCalls) {
                agentRepository.insertToolCallLog(
                    IdWorker.getId(),
                    taskId,
                    call.binding().toolId(),
                    call.requestPayloadJson(),
                    "{\"status\":\"WAITING_HUMAN_CONFIRM\"}",
                    "PENDING",
                    "WAITING_CONFIRM"
                );
            }
            audit(taskId, "TASK_WAITING_HUMAN_CONFIRM", "AGENT_TASK", taskId, "高风险工具等待人工确认", true);
            return toTaskResponse(agentRepository.getTaskOrThrow(taskId));
        }

        agentRepository.markTaskRunning(taskId, userId);
        audit(taskId, "TASK_RUNNING", "AGENT_TASK", taskId, "Agent任务开始执行", false);
        try {
            for (PreparedToolCall call : preparedToolCalls) {
                executeAndLogToolCall(taskId, call);
            }
            agentRepository.finishTask(taskId, "SUCCEEDED", null, userId);
            audit(taskId, "TASK_SUCCEEDED", "AGENT_TASK", taskId, "Agent任务执行成功", false);
        } catch (BizException e) {
            String errorMessage = safeSummary(e.getMessage());
            agentRepository.finishTask(taskId, "FAILED", errorMessage, userId);
            audit(taskId, "TASK_FAILED", "AGENT_TASK", taskId, errorMessage, true);
        }
        return toTaskResponse(agentRepository.getTaskOrThrow(taskId));
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentTaskResponse confirmCurrentEmployeeTask(Long taskId, ConfirmAgentTaskRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        AgentTaskRow task = getWaitingTaskForCurrentUser(taskId, userId);
        if (Boolean.FALSE.equals(request.getApproved())) {
            String summary = safeSummary(request.getComment());
            agentRepository.rejectWaitingTask(
                task.id(),
                writeJson(Map.of("status", "REJECTED", "comment", summary == null ? "" : summary)),
                userId
            );
            audit(task.id(), "TASK_HUMAN_REJECTED", "AGENT_TASK", task.id(), summary, true);
            return toTaskResponse(agentRepository.getTaskOrThrow(task.id()));
        }
        List<AgentPendingToolCallRow> pendingToolCalls = agentRepository.listPendingToolCalls(task.id());
        if (pendingToolCalls.isEmpty()) {
            throw new BizException(409, "Agent任务没有可继续执行的待确认工具");
        }
        agentRepository.markTaskRunning(task.id(), userId);
        audit(task.id(), "TASK_HUMAN_CONFIRMED", "AGENT_TASK", task.id(), safeSummary(request.getComment()), true);
        audit(task.id(), "TASK_RUNNING", "AGENT_TASK", task.id(), "人工确认后继续执行", false);
        try {
            for (AgentPendingToolCallRow pendingToolCall : pendingToolCalls) {
                executePendingToolCall(pendingToolCall);
            }
            agentRepository.finishTask(task.id(), "SUCCEEDED", null, userId);
            audit(task.id(), "TASK_SUCCEEDED", "AGENT_TASK", task.id(), "Agent任务执行成功", false);
        } catch (BizException e) {
            String errorMessage = safeSummary(e.getMessage());
            agentRepository.finishTask(task.id(), "FAILED", errorMessage, userId);
            audit(task.id(), "TASK_FAILED", "AGENT_TASK", task.id(), errorMessage, true);
        }
        return toTaskResponse(agentRepository.getTaskOrThrow(task.id()));
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentTaskResponse cancelCurrentEmployeeTask(Long taskId, CancelAgentTaskRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        AgentTaskRow task = getWaitingTaskForCurrentUser(taskId, userId);
        String reason = safeSummary(request.getReason());
        agentRepository.cancelWaitingTask(
            task.id(),
            writeJson(Map.of("status", "CANCELLED", "reason", reason == null ? "" : reason)),
            userId
        );
        audit(task.id(), "TASK_CANCELLED", "AGENT_TASK", task.id(), reason, true);
        return toTaskResponse(agentRepository.getTaskOrThrow(task.id()));
    }

    private AgentTaskRow getWaitingTaskForCurrentUser(Long taskId, Long userId) {
        AgentTaskRow task = agentRepository.getTaskForUpdateOrThrow(taskId);
        if (!"USER".equals(task.triggeredByType()) || !userId.equals(task.triggeredById())) {
            throw new BizException(403, "无权操作该Agent任务");
        }
        if (!"WAITING_HUMAN_CONFIRM".equals(task.status())) {
            throw new BizException(409, "只有等待人工确认的Agent任务可以操作");
        }
        if (task.waitingDeadlineAt() != null && task.waitingDeadlineAt().isBefore(LocalDateTime.now())) {
            throw new BizException(409, "Agent任务人工确认已超时");
        }
        return task;
    }

    private List<AgentToolCallRequest> safeToolCalls(CreateAgentTaskRequest request) {
        return request.getToolCalls() == null ? List.of() : request.getToolCalls();
    }

    private PreparedToolCall prepareToolCall(Long versionId, AgentToolCallRequest toolCall) {
        String toolCode = trimToRequired(toolCall.getToolCode(), "工具编码不能为空").toUpperCase(Locale.ROOT);
        AgentToolBindingRow binding = agentRepository.getBindingByVersionAndToolCode(versionId, toolCode);
        if (binding == null) {
            agentRepository.getActiveToolByCodeOrThrow(toolCode);
            throw new BizException(403, "Agent未授权调用该工具");
        }
        return new PreparedToolCall(binding, normalizeJson(toolCall.getRequestPayloadJson(), "工具请求载荷不是合法JSON"));
    }

    private void executeAndLogToolCall(Long taskId, PreparedToolCall call) {
        try {
            String responsePayload = toolExecutionService.execute(call.binding(), call.requestPayloadJson());
            agentRepository.insertToolCallLog(
                IdWorker.getId(),
                taskId,
                call.binding().toolId(),
                call.requestPayloadJson(),
                responsePayload,
                "APPROVED",
                "SUCCESS"
            );
        } catch (BizException e) {
            String errorMessage = safeSummary(e.getMessage());
            agentRepository.insertToolCallLog(
                IdWorker.getId(),
                taskId,
                call.binding().toolId(),
                call.requestPayloadJson(),
                writeJson(Map.of(
                    "status", "FAILED",
                    "errorMessage", errorMessage == null ? "工具执行失败" : errorMessage
                )),
                "APPROVED",
                "FAILED"
            );
            throw e;
        }
    }

    private void executePendingToolCall(AgentPendingToolCallRow pendingToolCall) {
        AgentToolBindingRow binding = new AgentToolBindingRow(
            null,
            null,
            pendingToolCall.toolId(),
            pendingToolCall.toolCode(),
            pendingToolCall.toolName(),
            pendingToolCall.toolType(),
            pendingToolCall.riskLevel(),
            pendingToolCall.readOnlyFlag(),
            pendingToolCall.approvalMode(),
            "ACTIVE",
            null,
            null
        );
        try {
            String responsePayload = toolExecutionService.execute(binding, pendingToolCall.requestPayloadJson());
            agentRepository.markToolCallApprovedSuccess(pendingToolCall.logId(), responsePayload);
        } catch (BizException e) {
            String errorMessage = safeSummary(e.getMessage());
            agentRepository.markToolCallApprovedFailed(
                pendingToolCall.logId(),
                writeJson(Map.of(
                    "status", "FAILED",
                    "errorMessage", errorMessage == null ? "工具执行失败" : errorMessage
                ))
            );
            throw e;
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

    private String normalizeIn(String value, Set<String> allowed, String message) {
        String normalized = trimToRequired(value, message).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BizException(400, message);
        }
        return normalized;
    }

    private String normalizeDefault(String value, String defaultValue, Set<String> allowed, String message) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return normalizeIn(value, allowed, message);
    }

    private String normalizePermission(String value) {
        String normalized = trimToRequired(value, "权限编码不能为空").toLowerCase(Locale.ROOT);
        if (!PERMISSIONS.contains(normalized)) {
            throw new BizException(400, "权限编码不支持");
        }
        return normalized;
    }

    private String trimToRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(400, message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String safeSummary(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 512 ? trimmed.substring(0, 512) : trimmed;
    }

    private AgentResponse toAgentResponse(AgentRow row) {
        AgentResponse response = new AgentResponse();
        response.setAgentId(row.id());
        response.setAgentCode(row.agentCode());
        response.setAgentName(row.agentName());
        response.setAgentType(row.agentType());
        response.setIdentityMode(row.identityMode());
        response.setPublishedVersionId(row.publishedVersionId());
        response.setStatus(row.status());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
    }

    private AgentVersionResponse toVersionResponse(AgentVersionRow row) {
        AgentVersionResponse response = new AgentVersionResponse();
        response.setVersionId(row.id());
        response.setAgentId(row.agentId());
        response.setVersionNo(row.versionNo());
        response.setModelConfigJson(row.modelConfigJson());
        response.setSystemPrompt(row.systemPrompt());
        response.setWorkflowConfigJson(row.workflowConfigJson());
        response.setPublishStatus(row.publishStatus());
        response.setPublishedAt(row.publishedAt());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
    }

    private AgentToolResponse toToolResponse(AgentToolRow row) {
        AgentToolResponse response = new AgentToolResponse();
        response.setToolId(row.id());
        response.setToolCode(row.toolCode());
        response.setToolName(row.toolName());
        response.setToolType(row.toolType());
        response.setConnectorId(row.connectorId());
        response.setRiskLevel(row.riskLevel());
        response.setReadOnlyFlag(row.readOnlyFlag());
        response.setStatus(row.status());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
    }

    private AgentToolBindingResponse toBindingResponse(AgentToolBindingRow row) {
        AgentToolBindingResponse response = new AgentToolBindingResponse();
        response.setBindingId(row.id());
        response.setAgentVersionId(row.agentVersionId());
        response.setToolId(row.toolId());
        response.setToolCode(row.toolCode());
        response.setToolName(row.toolName());
        response.setRiskLevel(row.riskLevel());
        response.setApprovalMode(row.approvalMode());
        response.setStatus(row.status());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
    }

    private AgentAclResponse toAclResponse(AgentAclRow row) {
        AgentAclResponse response = new AgentAclResponse();
        response.setAclId(row.id());
        response.setAgentId(row.agentId());
        response.setSubjectType(row.subjectType());
        response.setSubjectId(row.subjectId());
        response.setPermCode(row.permCode());
        response.setStatus(row.status());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
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

    private record PreparedToolCall(AgentToolBindingRow binding, String requestPayloadJson) {
    }
}
