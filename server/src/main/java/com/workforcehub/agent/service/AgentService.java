package com.workforcehub.agent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workforcehub.agent.dto.AdminAgentTaskAuditResponse;
import com.workforcehub.agent.dto.AdminAgentTaskFailureSummaryResponse;
import com.workforcehub.agent.dto.AdminAgentTaskMetricsResponse;
import com.workforcehub.agent.dto.AdminAgentTaskResponse;
import com.workforcehub.agent.dto.AdminAgentTaskTimeoutRunResponse;
import com.workforcehub.agent.dto.AdminAgentTaskToolCallResponse;
import com.workforcehub.agent.dto.CancelAgentTaskRequest;
import com.workforcehub.agent.dto.ConfirmAgentTaskRequest;
import com.workforcehub.agent.model.AgentModelClient;
import com.workforcehub.agent.model.AgentModelRequest;
import com.workforcehub.agent.dto.AgentAclResponse;
import com.workforcehub.agent.dto.AgentResponse;
import com.workforcehub.agent.dto.AgentTaskListQuery;
import com.workforcehub.agent.dto.AgentTaskResponse;
import com.workforcehub.agent.dto.AgentTaskTimeoutRunQuery;
import com.workforcehub.agent.dto.AgentToolBindingResponse;
import com.workforcehub.agent.dto.AgentToolCallRequest;
import com.workforcehub.agent.dto.AgentToolResponse;
import com.workforcehub.agent.dto.AgentVersionResponse;
import com.workforcehub.agent.dto.BindAgentToolRequest;
import com.workforcehub.agent.dto.CreateAgentRequest;
import com.workforcehub.agent.dto.CreateAgentTaskRequest;
import com.workforcehub.agent.dto.CreateAgentToolRequest;
import com.workforcehub.agent.dto.CreateAgentVersionRequest;
import com.workforcehub.agent.dto.CreateLlmModelRequest;
import com.workforcehub.agent.dto.GrantAgentAclRequest;
import com.workforcehub.agent.dto.LlmModelResponse;
import com.workforcehub.agent.dto.ModelTestResponse;
import com.workforcehub.agent.dto.TrialRunRequest;
import com.workforcehub.agent.dto.TrialRunResponse;
import com.workforcehub.agent.dto.UpdateAgentVersionRequest;
import com.workforcehub.agent.dto.UpdateLlmModelRequest;
import com.workforcehub.agent.dto.WorkflowRunRequest;
import com.workforcehub.agent.dto.WorkflowRunResponse;
import com.workforcehub.agent.dto.WorkflowValidationResponse;
import com.workforcehub.agent.repository.AgentAclRow;
import com.workforcehub.agent.repository.AgentRepository;
import com.workforcehub.agent.repository.AgentRow;
import com.workforcehub.agent.repository.LlmModelRow;
import com.workforcehub.agent.repository.AgentTaskQueryCriteria;
import com.workforcehub.agent.repository.AgentTaskTimeoutRepository;
import com.workforcehub.agent.repository.AgentTaskTimeoutRunQueryCriteria;
import com.workforcehub.agent.repository.AdminAgentTaskAuditRow;
import com.workforcehub.agent.repository.AdminAgentTaskFailureSummaryRow;
import com.workforcehub.agent.repository.AdminAgentTaskMetricsRow;
import com.workforcehub.agent.repository.AdminAgentTaskRow;
import com.workforcehub.agent.repository.AdminAgentTaskTimeoutRunRow;
import com.workforcehub.agent.repository.AdminAgentTaskToolCallRow;
import com.workforcehub.agent.repository.AgentPendingToolCallRow;
import com.workforcehub.agent.repository.AgentTaskRow;
import com.workforcehub.agent.repository.AgentToolBindingRow;
import com.workforcehub.agent.repository.AgentToolRow;
import com.workforcehub.agent.repository.AgentVersionRow;
import com.workforcehub.agent.workflow.WorkflowEdge;
import com.workforcehub.agent.workflow.WorkflowExecutionResult;
import com.workforcehub.agent.workflow.WorkflowExecutor;
import com.workforcehub.agent.workflow.WorkflowParser;
import com.workforcehub.agent.workflow.WorkflowValidationResult;
import com.workforcehub.agent.workflow.WorkflowValidator;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.spi.ImAgentDescriptor;
import com.workforcehub.spi.ImAgentTriggerCommand;
import com.workforcehub.spi.ImAgentTriggerResult;
import com.workforcehub.spi.ImAgentTriggerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * module-agent 第一批治理、授权、任务和工具调用服务。
 */
@Service
public class AgentService implements ImAgentTriggerService {

    private static final Set<String> AGENT_TYPES = Set.of("QA", "PRE_REVIEW", "REMINDER", "AUTOMATION");
    private static final Set<String> IDENTITY_MODES = Set.of("SYSTEM_IDENTITY", "USER_DELEGATED");
    private static final Set<String> TOOL_TYPES = Set.of("SPI", "CONNECTOR");
    private static final Set<String> RISK_LEVELS = Set.of("READONLY", "LOW", "MEDIUM", "HIGH");
    private static final Set<String> RECORD_STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> SUBJECT_TYPES = Set.of("USER", "DEPARTMENT", "GROUP", "ROLE", "ADMIN_USER");
    private static final Set<String> PERMISSIONS = Set.of("visible", "use", "manage");
    private static final Set<String> TRIGGER_TYPES = Set.of("MANUAL", "CONVERSATION", "WORKFLOW", "TIMER", "EVENT");
    private static final Set<String> TASK_STATUSES = Set.of("PENDING", "RUNNING", "SUCCEEDED", "FAILED",
        "WAITING_HUMAN_CONFIRM", "REJECTED", "CANCELLED");
    private static final Set<String> TRIGGERED_BY_TYPES = Set.of("USER", "ADMIN", "SYSTEM", "WORKFLOW", "TIMER", "EVENT");
    private static final Set<String> APPROVAL_MODES = Set.of("AUTO", "REQUIRE_CONFIRM");
    private static final Set<String> TIMEOUT_RUN_TRIGGER_TYPES = Set.of("SERVICE", "MANUAL", "SCHEDULED");
    private static final Set<String> TIMEOUT_RUN_STATUSES = Set.of("RUNNING", "SUCCESS", "SKIPPED", "FAILED");

    private final AgentRepository agentRepository;
    private final AgentTaskTimeoutRepository timeoutRepository;
    private final ObjectMapper objectMapper;
    private final AgentToolExecutionService toolExecutionService;
    private final AgentTaskExecutionService agentTaskExecutionService;
    private final WorkflowParser workflowParser;
    private final WorkflowValidator workflowValidator;
    private final WorkflowExecutor workflowExecutor;
    private final AgentModelClient agentModelClient;

    public AgentService(AgentRepository agentRepository,
                        AgentTaskTimeoutRepository timeoutRepository,
                        ObjectMapper objectMapper,
                        AgentToolExecutionService toolExecutionService,
                        AgentTaskExecutionService agentTaskExecutionService,
                        WorkflowParser workflowParser,
                        WorkflowValidator workflowValidator,
                        WorkflowExecutor workflowExecutor,
                        AgentModelClient agentModelClient) {
        this.agentRepository = agentRepository;
        this.timeoutRepository = timeoutRepository;
        this.objectMapper = objectMapper;
        this.toolExecutionService = toolExecutionService;
        this.agentTaskExecutionService = agentTaskExecutionService;
        this.workflowParser = workflowParser;
        this.workflowValidator = workflowValidator;
        this.workflowExecutor = workflowExecutor;
        this.agentModelClient = agentModelClient;
    }

    public PageResult<AgentResponse> pageAdminAgents(long pageNo, long pageSize) {
        return pageAdminAgents(pageNo, pageSize, null);
    }

    public PageResult<AgentResponse> pageAdminAgents(long pageNo, long pageSize, String keyword) {
        long safePageNo = Math.max(pageNo, 1L);
        long safePageSize = Math.min(Math.max(pageSize, 1L), 100L);
        String normalizedKeyword = trimToNull(keyword);
        long total = agentRepository.countAgents(normalizedKeyword);
        List<AgentResponse> rows = agentRepository.pageAgents(normalizedKeyword, (safePageNo - 1) * safePageSize, safePageSize).stream()
            .map(this::toAgentResponse)
            .toList();
        return PageResult.of(safePageNo, safePageSize, total, rows);
    }

    public AgentResponse getAdminAgent(Long agentId) {
        return toAgentResponse(agentRepository.getAgentOrThrow(agentId));
    }

    public List<AgentVersionResponse> listAdminAgentVersions(Long agentId) {
        agentRepository.getAgentOrThrow(agentId);
        return agentRepository.listVersionsByAgent(agentId).stream()
            .map(this::toVersionResponse)
            .toList();
    }

    public List<AgentToolResponse> listAdminAgentTools() {
        return agentRepository.listTools().stream()
            .map(this::toToolResponse)
            .toList();
    }

    public List<AgentAclResponse> listAdminAgentAcl(Long agentId) {
        agentRepository.getAgentOrThrow(agentId);
        return agentRepository.listAclByAgent(agentId).stream()
            .map(this::toAclResponse)
            .toList();
    }

    public PageResult<AdminAgentTaskResponse> pageAdminAgentTasks(AgentTaskListQuery query) {
        long safePageNo = Math.max(query.getPageNo(), 1L);
        long safePageSize = Math.min(Math.max(query.getPageSize(), 1L), 100L);
        AgentTaskQueryCriteria criteria = toTaskQueryCriteria(query);
        long total = agentRepository.countAdminAgentTasks(criteria);
        List<AdminAgentTaskResponse> rows = agentRepository.pageAdminAgentTasks(
                criteria,
                (safePageNo - 1) * safePageSize,
                safePageSize
            ).stream()
            .map(this::toAdminTaskResponse)
            .toList();
        return PageResult.of(safePageNo, safePageSize, total, rows);
    }

    public AdminAgentTaskResponse getAdminAgentTask(Long taskId) {
        return toAdminTaskResponse(agentRepository.getAdminAgentTaskOrThrow(taskId));
    }

    public WorkflowValidationResponse validateWorkflow(String workflowConfigJson) {
        WorkflowValidationResult result = workflowValidator.validate(workflowConfigJson);
        WorkflowValidationResponse response = new WorkflowValidationResponse();
        response.setValid(result.valid());
        response.setMessages(result.messages());
        return response;
    }

    public AdminAgentTaskMetricsResponse getAdminAgentTaskMetrics() {
        return toAdminTaskMetricsResponse(agentRepository.getAdminAgentTaskMetrics());
    }

    public List<AdminAgentTaskFailureSummaryResponse> listAdminAgentTaskFailureSummary() {
        return agentRepository.listAdminAgentTaskFailureSummary().stream()
            .map(this::toAdminTaskFailureSummaryResponse)
            .toList();
    }

    public PageResult<AdminAgentTaskTimeoutRunResponse> pageAdminAgentTaskTimeoutRuns(AgentTaskTimeoutRunQuery query) {
        long safePageNo = Math.max(query.getPageNo(), 1L);
        long safePageSize = Math.min(Math.max(query.getPageSize(), 1L), 100L);
        AgentTaskTimeoutRunQueryCriteria criteria = toTimeoutRunQueryCriteria(query);
        long total = timeoutRepository.countAdminAgentTaskTimeoutRuns(criteria);
        List<AdminAgentTaskTimeoutRunResponse> rows = timeoutRepository.pageAdminAgentTaskTimeoutRuns(
                criteria,
                (safePageNo - 1) * safePageSize,
                safePageSize
            ).stream()
            .map(this::toAdminTaskTimeoutRunResponse)
            .toList();
        return PageResult.of(safePageNo, safePageSize, total, rows);
    }

    public List<AdminAgentTaskToolCallResponse> listAdminAgentTaskToolCalls(Long taskId) {
        agentRepository.getAdminAgentTaskOrThrow(taskId);
        return agentRepository.listAdminAgentTaskToolCalls(taskId).stream()
            .map(this::toAdminTaskToolCallResponse)
            .toList();
    }

    public List<AdminAgentTaskAuditResponse> listAdminAgentTaskAudits(Long taskId) {
        agentRepository.getAdminAgentTaskOrThrow(taskId);
        return agentRepository.listAdminAgentTaskAudits(taskId).stream()
            .map(this::toAdminTaskAuditResponse)
            .toList();
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
        Integer versionNo = request.getVersionNo() == null ? agentRepository.nextAgentVersionNo(agentId) : request.getVersionNo();
        String versionLabel = trimToNull(request.getVersionLabel());
        if (agentRepository.existsAgentVersion(agentId, versionNo)) {
            throw new BizException(409, "Agent版本号已存在");
        }
        if (versionLabel != null && agentRepository.existsAgentVersionLabel(agentId, versionLabel)) {
            throw new BizException(409, "Agent显示版本号已存在");
        }
        Long versionId = IdWorker.getId();
        agentRepository.insertAgentVersion(
            versionId,
            agentId,
            versionNo,
            versionLabel,
            normalizeJson(request.getModelConfigJson(), "模型配置不是合法JSON"),
            trimToNull(request.getSystemPrompt()),
            normalizeJson(request.getWorkflowConfigJson(), "工作流配置不是合法JSON"),
            operatorId
        );
        return toVersionResponse(agentRepository.getVersionOrThrow(versionId));
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentVersionResponse updateVersion(Long versionId, UpdateAgentVersionRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        AgentVersionRow version = agentRepository.getVersionOrThrow(versionId);
        if (!"DRAFT".equals(version.publishStatus())) {
            throw new BizException(409, "只有草稿版本可以修改");
        }
        agentRepository.updateAgentVersion(
            versionId,
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
        assertValidWorkflow(version.workflowConfigJson());
        agentRepository.getAgentOrThrow(version.agentId());
        if (!agentRepository.hasActiveUsableAcl(version.agentId())) {
            throw new BizException(400, "请先配置Agent可用范围");
        }
        if (workflowParser.isWorkflowConfig(version.workflowConfigJson())) {
            String expectedSignature = workflowTestSignature(
                version.workflowConfigJson(),
                version.systemPrompt(),
                version.modelConfigJson()
            );
            if (!expectedSignature.equals(version.workflowTestSignature())) {
                throw new BizException(400, "请先完成一次流程测试");
            }
        }
        agentRepository.publishVersion(version.agentId(), versionId, operatorId);
        return toAgentResponse(agentRepository.getAgentOrThrow(version.agentId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentResponse disableAgent(Long agentId) {
        Long operatorId = CurrentUserHolder.requireUserId();
        agentRepository.getAgentOrThrow(agentId);
        agentRepository.disableAgent(agentId, operatorId);
        return toAgentResponse(agentRepository.getAgentOrThrow(agentId));
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentResponse enableAgent(Long agentId) {
        Long operatorId = CurrentUserHolder.requireUserId();
        AgentRow agent = agentRepository.getAgentOrThrow(agentId);
        if (agent.publishedVersionId() == null) {
            throw new BizException(400, "未发布Agent不能启用");
        }
        agentRepository.enableAgent(agentId, operatorId);
        return toAgentResponse(agentRepository.getAgentOrThrow(agentId));
    }

    public List<AgentResponse> listCurrentEmployeeAgents(String keyword) {
        Long userId = CurrentUserHolder.requireUserId();
        return agentRepository.listUsableAgentsForUser(userId, trimToNull(keyword)).stream()
            .map(this::toAgentResponse)
            .toList();
    }

    @Override
    public ImAgentDescriptor requireUsableAgent(Long agentId, Long userId) {
        AgentRow agent = agentRepository.getAgentOrThrow(agentId);
        if (agent.publishedVersionId() == null || !"ACTIVE".equals(agent.status())) {
            throw new BizException(409, "Agent未发布或未启用");
        }
        if (!agentRepository.canUseAgent(agentId, userId)) {
            throw new BizException(403, "无权使用该Agent");
        }
        return new ImAgentDescriptor(agent.id(), agent.agentCode(), agent.agentName());
    }

    @Override
    public ImAgentTriggerResult triggerConversationReply(ImAgentTriggerCommand command) {
        if (command == null) {
            throw new BizException(400, "Agent会话触发命令不能为空");
        }
        if (!"USER".equals(command.triggerSenderType())) {
            throw new BizException(400, "仅支持员工消息触发会话Agent");
        }
        AgentTaskResponse response = agentTaskExecutionService.createConversationReplyTask(command);
        return new ImAgentTriggerResult(response.getTaskId(), response.getStatus(), command.idempotencyKey());
    }

    @Transactional(rollbackFor = Exception.class)
    public AgentTaskResponse createCurrentEmployeeTask(Long agentId, CreateAgentTaskRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        AgentRow agent = agentRepository.getAgentOrThrow(agentId);
        if (agent.publishedVersionId() == null || !"ACTIVE".equals(agent.status())) {
            throw new BizException(409, "Agent未发布或未启用");
        }
        if (!agentRepository.canUseAgent(agentId, userId)) {
            throw new BizException(403, "无权使用该Agent");
        }
        String inputPayloadJson = normalizeJson(request.getInputPayloadJson(), "输入载荷不是合法JSON");
        String triggerType = normalizeIn(request.getTriggerType(), TRIGGER_TYPES, "触发类型不支持");
        AgentVersionRow version = agentRepository.getVersionOrThrow(agent.publishedVersionId());
        boolean workflowTask = workflowParser.isWorkflowConfig(version.workflowConfigJson()) && safeToolCalls(request).isEmpty();

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
        if (workflowTask) {
            agentRepository.updateTaskWorkflowSnapshot(taskId, version.workflowConfigJson(), userId);
        }

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
            if (workflowTask) {
                WorkflowExecutionResult result = workflowExecutor.execute(
                    taskId,
                    agent.id(),
                    agent.agentName(),
                    version,
                    inputPayloadJson,
                    userId
                );
                agentRepository.updateTaskExecutionTrace(taskId, result.traceJson(), userId);
            } else {
                for (PreparedToolCall call : preparedToolCalls) {
                    executeAndLogToolCall(taskId, call);
                }
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

    private AgentTaskQueryCriteria toTaskQueryCriteria(AgentTaskListQuery query) {
        return new AgentTaskQueryCriteria(
            normalizeOptionalIn(query.getStatus(), TASK_STATUSES, "Agent任务状态不支持"),
            query.getAgentId(),
            normalizeOptionalIn(query.getTriggeredByType(), TRIGGERED_BY_TYPES, "触发主体类型不支持"),
            query.getTriggeredById(),
            trimToNull(query.getContextType()),
            query.getCreatedFrom(),
            query.getCreatedTo()
        );
    }

    private AgentTaskTimeoutRunQueryCriteria toTimeoutRunQueryCriteria(AgentTaskTimeoutRunQuery query) {
        return new AgentTaskTimeoutRunQueryCriteria(
            normalizeOptionalIn(query.getTriggerType(), TIMEOUT_RUN_TRIGGER_TYPES, "Agent超时扫描触发类型不支持"),
            normalizeOptionalIn(query.getStatus(), TIMEOUT_RUN_STATUSES, "Agent超时扫描批次状态不支持"),
            query.getStartedFrom(),
            query.getStartedTo()
        );
    }

    private String normalizeOptionalIn(String value, Set<String> allowed, String message) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BizException(400, message);
        }
        return normalized;
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
        response.setPublishedVersionNo(row.publishedVersionNo());
        response.setPublishedVersionLabel(row.publishedVersionLabel());
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
        response.setVersionLabel(row.versionLabel());
        response.setModelConfigJson(row.modelConfigJson());
        response.setSystemPrompt(row.systemPrompt());
        response.setWorkflowConfigJson(row.workflowConfigJson());
        response.setPublishStatus(row.publishStatus());
        response.setWorkflowTestSignature(row.workflowTestSignature());
        response.setWorkflowTestedAt(row.workflowTestedAt());
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
        response.setWorkflowSnapshotJson(row.workflowSnapshotJson());
        response.setExecutionTraceJson(row.executionTraceJson());
        response.setStartedAt(row.startedAt());
        response.setFinishedAt(row.finishedAt());
        response.setErrorMessage(row.errorMessage());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
    }

    private AdminAgentTaskResponse toAdminTaskResponse(AdminAgentTaskRow row) {
        AdminAgentTaskResponse response = new AdminAgentTaskResponse();
        response.setTaskId(row.id());
        response.setAgentId(row.agentId());
        response.setAgentCode(row.agentCode());
        response.setAgentName(row.agentName());
        response.setAgentVersionId(row.agentVersionId());
        response.setTriggerType(row.triggerType());
        response.setContextType(row.contextType());
        response.setContextId(row.contextId());
        response.setTriggeredByType(row.triggeredByType());
        response.setTriggeredById(row.triggeredById());
        response.setStatus(row.status());
        response.setToolCallCount(row.toolCallCount());
        response.setWaitingDeadlineAt(row.waitingDeadlineAt());
        response.setWorkflowSnapshotJson(row.workflowSnapshotJson());
        response.setExecutionTraceJson(row.executionTraceJson());
        response.setStartedAt(row.startedAt());
        response.setFinishedAt(row.finishedAt());
        response.setErrorMessage(row.errorMessage());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
    }

    private AdminAgentTaskToolCallResponse toAdminTaskToolCallResponse(AdminAgentTaskToolCallRow row) {
        AdminAgentTaskToolCallResponse response = new AdminAgentTaskToolCallResponse();
        response.setLogId(row.id());
        response.setTaskId(row.taskId());
        response.setToolId(row.toolId());
        response.setToolCode(row.toolCode());
        response.setToolName(row.toolName());
        response.setToolType(row.toolType());
        response.setRiskLevel(row.riskLevel());
        response.setReadOnlyFlag(row.readOnlyFlag());
        response.setRequestPayloadJson(row.requestPayloadJson());
        response.setResponsePayloadJson(row.responsePayloadJson());
        response.setApprovalStatus(row.approvalStatus());
        response.setResultStatus(row.resultStatus());
        response.setCalledAt(row.calledAt());
        return response;
    }

    private AdminAgentTaskMetricsResponse toAdminTaskMetricsResponse(AdminAgentTaskMetricsRow row) {
        AdminAgentTaskMetricsResponse response = new AdminAgentTaskMetricsResponse();
        response.setTotalTaskCount(row.totalTaskCount());
        response.setPendingTaskCount(row.pendingTaskCount());
        response.setRunningTaskCount(row.runningTaskCount());
        response.setSucceededTaskCount(row.succeededTaskCount());
        response.setFailedTaskCount(row.failedTaskCount());
        response.setWaitingHumanConfirmTaskCount(row.waitingHumanConfirmTaskCount());
        response.setRejectedTaskCount(row.rejectedTaskCount());
        response.setCancelledTaskCount(row.cancelledTaskCount());
        response.setTotalToolCallCount(row.totalToolCallCount());
        response.setFailedToolCallCount(row.failedToolCallCount());
        response.setAverageFinishedDurationMillis(row.averageFinishedDurationMillis());
        response.setLastTaskCreatedAt(row.lastTaskCreatedAt());
        return response;
    }

    private AdminAgentTaskFailureSummaryResponse toAdminTaskFailureSummaryResponse(AdminAgentTaskFailureSummaryRow row) {
        AdminAgentTaskFailureSummaryResponse response = new AdminAgentTaskFailureSummaryResponse();
        response.setErrorMessage(row.errorMessage());
        response.setTaskCount(row.taskCount());
        response.setToolCallCount(row.toolCallCount());
        response.setFirstOccurredAt(row.firstOccurredAt());
        response.setLastOccurredAt(row.lastOccurredAt());
        return response;
    }

    private AdminAgentTaskTimeoutRunResponse toAdminTaskTimeoutRunResponse(AdminAgentTaskTimeoutRunRow row) {
        AdminAgentTaskTimeoutRunResponse response = new AdminAgentTaskTimeoutRunResponse();
        response.setJobRunId(row.jobRunId());
        response.setTriggerType(row.triggerType());
        response.setStatus(row.status());
        response.setBatchSize(row.batchSize());
        response.setScannedCount(row.scannedCount());
        response.setCancelledCount(row.cancelledCount());
        response.setSkippedCount(row.skippedCount());
        response.setLockOwner(row.lockOwner());
        response.setErrorMessage(row.errorMessage());
        response.setStartedAt(row.startedAt());
        response.setFinishedAt(row.finishedAt());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
    }

    private AdminAgentTaskAuditResponse toAdminTaskAuditResponse(AdminAgentTaskAuditRow row) {
        AdminAgentTaskAuditResponse response = new AdminAgentTaskAuditResponse();
        response.setAuditId(row.id());
        response.setTaskId(row.taskId());
        response.setActionType(row.actionType());
        response.setTargetType(row.targetType());
        response.setTargetId(row.targetId());
        response.setDecisionSummary(row.decisionSummary());
        response.setRiskFlag(row.riskFlag());
        response.setOccurredAt(row.occurredAt());
        return response;
    }

    private record PreparedToolCall(AgentToolBindingRow binding, String requestPayloadJson) {
    }

    private void assertValidWorkflow(String workflowConfigJson) {
        if (!workflowParser.isWorkflowConfig(workflowConfigJson)) {
            return;
        }
        WorkflowValidationResult validation = workflowValidator.validate(workflowConfigJson);
        if (!validation.valid()) {
            throw new BizException(400, "工作流配置无效: " + String.join("; ", validation.messages()));
        }
    }

    // ==================== LLM Model ====================

    public List<LlmModelResponse> listLlmModels() {
        return agentRepository.listLlmModels().stream().map(this::toLlmModelResponse).toList();
    }

    public LlmModelResponse createLlmModel(CreateLlmModelRequest request) {
        Long modelId = IdWorker.getId();
        agentRepository.insertLlmModel(modelId, request.provider(), request.modelName(),
            request.displayName(), request.endpoint(), request.apiKey(),
            request.status() != null ? request.status() : "ACTIVE", 0L);
        return toLlmModelResponse(agentRepository.getLlmModel(modelId));
    }

    public LlmModelResponse updateLlmModel(Long modelId, UpdateLlmModelRequest request) {
        LlmModelRow existing = agentRepository.getLlmModel(modelId);
        if (existing == null) {
            throw new BizException(404, "模型不存在");
        }
        String apiKey = request.apiKey() != null ? request.apiKey() : existing.apiKey();
        agentRepository.updateLlmModel(modelId, request.provider(), request.modelName(),
            request.displayName(), request.endpoint(), apiKey,
            request.status() != null ? request.status() : existing.status(), 0L);
        return toLlmModelResponse(agentRepository.getLlmModel(modelId));
    }

    public void deleteLlmModel(Long modelId) {
        agentRepository.deleteLlmModel(modelId, 0L);
    }

    private LlmModelResponse toLlmModelResponse(LlmModelRow row) {
        if (row == null) return null;
        return new LlmModelResponse(
            String.valueOf(row.id()),
            row.provider(),
            row.modelName(),
            row.displayName(),
            row.endpoint(),
            row.status(),
            row.createdAt(),
            row.updatedAt()
        );
    }

    // ==================== Trial Run ====================

    public TrialRunResponse trialRun(TrialRunRequest request) {
        long start = System.currentTimeMillis();
        try {
            if (!workflowParser.isWorkflowConfig(request.workflowConfigJson())) {
                return new TrialRunResponse(false, null, "无效的工作流配置", System.currentTimeMillis() - start);
            }

            var definition = workflowParser.parse(request.workflowConfigJson());
            var targetNode = definition.nodes().stream()
                .filter(n -> n.id().equals(request.nodeId()))
                .findFirst()
                .orElse(null);

            if (targetNode == null) {
                return new TrialRunResponse(false, null, "节点不存在: " + request.nodeId(), System.currentTimeMillis() - start);
            }

            Map<String, Object> input = new java.util.HashMap<>();
            if (request.inputJson() != null && !request.inputJson().isBlank()) {
                String raw = request.inputJson().trim();
                if (raw.startsWith("{") || raw.startsWith("[")) {
                    try {
                        input = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<>() {});
                    } catch (Exception e) {
                        return new TrialRunResponse(false, null, "输入JSON解析失败: " + e.getMessage(), System.currentTimeMillis() - start);
                    }
                } else {
                    input.put("query", raw);
                }
            }

            var result = workflowExecutor.executeNode(targetNode, definition, input,
                request.systemPrompt(), request.modelConfigJson());
            String outputJson = new com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter().writeValueAsString(result);
            return new TrialRunResponse(true, outputJson, null, System.currentTimeMillis() - start);

        } catch (Exception e) {
            return new TrialRunResponse(false, null, e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    // ==================== Model Test ====================

    public ModelTestResponse testModelConnection(Long modelId) {
        long start = System.currentTimeMillis();
        try {
            var modelRow = agentRepository.getLlmModel(modelId);
            if (modelRow == null) {
                return new ModelTestResponse(false, "模型不存在", null, System.currentTimeMillis() - start);
            }
            if (!StringUtils.hasText(modelRow.apiKey())) {
                return new ModelTestResponse(false, "未配置 API Key", null, System.currentTimeMillis() - start);
            }

            String modelConfigJson = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(java.util.Map.of(
                    "baseUrl", modelRow.endpoint() != null ? modelRow.endpoint() : "",
                    "apiKey", modelRow.apiKey(),
                    "model", modelRow.modelName()
                ));

            var response = agentModelClient.generateReply(new AgentModelRequest(
                0L, 0L, "test", "你是一个测试助手。", "请回复：连接成功", modelConfigJson
            ));

            return new ModelTestResponse(true, "连接成功", response.text(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new ModelTestResponse(false, e.getMessage(), null, System.currentTimeMillis() - start);
        }
    }

    // ==================== Workflow Run ====================

    public WorkflowRunResponse runWorkflow(WorkflowRunRequest request) {
        long start = System.currentTimeMillis();
        try {
            AgentVersionRow version = null;
            String workflowConfigJson = request.workflowConfigJson();
            String systemPrompt = request.systemPrompt();
            String modelConfigJson = request.modelConfigJson();
            if (request.versionId() != null) {
                version = agentRepository.getVersionOrThrow(request.versionId());
                if (!"DRAFT".equals(version.publishStatus())) {
                    return new WorkflowRunResponse(false, null, List.of(), "只有草稿版本可以测试", System.currentTimeMillis() - start);
                }
                if (!StringUtils.hasText(workflowConfigJson)) workflowConfigJson = version.workflowConfigJson();
                if (!StringUtils.hasText(systemPrompt)) systemPrompt = version.systemPrompt();
                if (!StringUtils.hasText(modelConfigJson)) modelConfigJson = version.modelConfigJson();
            }
            if (!workflowParser.isWorkflowConfig(workflowConfigJson)) {
                return new WorkflowRunResponse(false, null, List.of(), "无效的工作流配置", System.currentTimeMillis() - start);
            }

            var definition = workflowParser.parse(workflowConfigJson);
            var startNode = definition.startNode();
            if (startNode == null) {
                return new WorkflowRunResponse(false, null, List.of(), "缺少 START 节点", System.currentTimeMillis() - start);
            }

            var context = new com.workforcehub.agent.workflow.WorkflowContext(objectMapper);
            Map<String, Object> input = new java.util.HashMap<>();
            if (request.inputJson() != null && !request.inputJson().isBlank()) {
                String raw = request.inputJson().trim();
                if (raw.startsWith("{") || raw.startsWith("[")) {
                    input = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<>() {});
                } else {
                    input.put("query", raw);
                }
            }
            input.putIfAbsent(StringUtils.hasText(startNode.outputKey()) ? startNode.outputKey() : startNode.id(), Map.copyOf(input));
            input.forEach((k, v) -> {
                if (v instanceof String s) context.put(k, s);
                else context.putJson(k, objectMapper.valueToTree(v));
            });

            List<WorkflowRunResponse.StepTrace> steps = new java.util.ArrayList<>();
            String firstNext = definition.edges().stream()
                .filter(e -> e.from().equals(startNode.id()))
                .findFirst().map(WorkflowEdge::to).orElse(null);
            var current = firstNext != null ? definition.node(firstNext) : null;
            String replyText = null;
            int maxSteps = 50;

            for (int i = 0; i < maxSteps && current != null; i++) {
                long stepStart = System.currentTimeMillis();
                try {
                    var result = workflowExecutor.executeNode(current, definition,
                        input, systemPrompt, modelConfigJson, version);

                    if (result.containsKey("output")) {
                        String outputKey = StringUtils.hasText(current.outputKey()) ? current.outputKey() : current.id();
                        input.put(outputKey, result.get("output"));
                    }
                    String status = (String) result.getOrDefault("status", "UNKNOWN");
                    String output = result.containsKey("output")
                        ? objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result.get("output"))
                        : result.containsKey("error")
                            ? String.valueOf(result.get("error"))
                        : null;
                    String nextNodeId = (String) result.get("nextNodeId");
                    if (nextNodeId == null && current.type() != com.workforcehub.agent.workflow.WorkflowNodeType.END) {
                        String currentNodeId = current.id();
                        nextNodeId = definition.edges().stream()
                            .filter(edge -> edge.from().equals(currentNodeId))
                            .findFirst()
                            .map(WorkflowEdge::to)
                            .orElse(null);
                    }

                    steps.add(new WorkflowRunResponse.StepTrace(
                        current.id(), current.type().name(), status, output, nextNodeId,
                        System.currentTimeMillis() - stepStart
                    ));

                    if ("FAILED".equals(status)) {
                        return new WorkflowRunResponse(false, null, steps,
                            "节点 " + current.id() + " 执行失败: " + (output == null ? "未知错误" : output),
                            System.currentTimeMillis() - start);
                    }

                    if (current.type() == com.workforcehub.agent.workflow.WorkflowNodeType.END) {
                        replyText = result.containsKey("reply") ? String.valueOf(result.get("reply")) : output;
                        break;
                    }

                    if (nextNodeId != null) {
                        current = definition.node(nextNodeId);
                    } else {
                        break;
                    }
                } catch (Exception e) {
                    steps.add(new WorkflowRunResponse.StepTrace(
                        current.id(), current.type().name(), "FAILED", e.getMessage(), null,
                        System.currentTimeMillis() - stepStart
                    ));
                    return new WorkflowRunResponse(false, null, steps, "节点 " + current.id() + " 执行失败: " + e.getMessage(),
                        System.currentTimeMillis() - start);
                }
            }

            if (replyText == null) {
                return new WorkflowRunResponse(false, null, steps, "工作流未到达 END 节点", System.currentTimeMillis() - start);
            }
            if (version != null) {
                String normalizedWorkflow = normalizeJson(workflowConfigJson, "工作流配置不是合法JSON");
                String normalizedModel = normalizeJson(modelConfigJson, "模型配置不是合法JSON");
                String normalizedPrompt = trimToNull(systemPrompt);
                agentRepository.updateAgentVersionWorkflowTest(
                    version.id(),
                    normalizedModel,
                    normalizedPrompt,
                    normalizedWorkflow,
                    workflowTestSignature(normalizedWorkflow, normalizedPrompt, normalizedModel),
                    CurrentUserHolder.requireUserId()
                );
            }
            return new WorkflowRunResponse(true, replyText, steps, null, System.currentTimeMillis() - start);

        } catch (Exception e) {
            return new WorkflowRunResponse(false, null, List.of(), e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private String workflowTestSignature(String workflowConfigJson, String systemPrompt, String modelConfigJson) {
        try {
            String payload = normalizeJsonStable(workflowConfigJson, "工作流配置不是合法JSON") + "\n"
                + (trimToNull(systemPrompt) == null ? "" : trimToNull(systemPrompt)) + "\n"
                + (normalizeJsonStable(modelConfigJson, "模型配置不是合法JSON") == null ? "" : normalizeJsonStable(modelConfigJson, "模型配置不是合法JSON"));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new BizException(400, e.getMessage());
        }
    }

    private String normalizeJsonStable(String value, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sortJson(objectMapper.readTree(value)));
        } catch (Exception e) {
            throw new BizException(400, errorMessage);
        }
    }

    private JsonNode sortJson(JsonNode node) {
        if (node == null || !node.isContainerNode()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            node.forEach(child -> array.add(sortJson(child)));
            return array;
        }
        ObjectNode object = objectMapper.createObjectNode();
        TreeMap<String, JsonNode> fields = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            fields.put(entry.getKey(), entry.getValue());
        }
        fields.forEach((key, value) -> object.set(key, sortJson(value)));
        return object;
    }
}
