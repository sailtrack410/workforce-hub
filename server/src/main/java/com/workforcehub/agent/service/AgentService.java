package com.workforcehub.agent.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.workforcehub.agent.repository.AgentTaskRow;
import com.workforcehub.agent.repository.AgentToolBindingRow;
import com.workforcehub.agent.repository.AgentToolRow;
import com.workforcehub.agent.repository.AgentVersionRow;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.spi.AttendanceQueryService;
import com.workforcehub.spi.KnowledgeSearchService;
import com.workforcehub.spi.LeaveQueryService;
import com.workforcehub.spi.OrgQueryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
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
    private final OrgQueryService orgQueryService;
    private final ObjectProvider<LeaveQueryService> leaveQueryServiceProvider;
    private final ObjectProvider<AttendanceQueryService> attendanceQueryServiceProvider;
    private final ObjectProvider<KnowledgeSearchService> knowledgeSearchServiceProvider;

    public AgentService(AgentRepository agentRepository,
                        ObjectMapper objectMapper,
                        OrgQueryService orgQueryService,
                        ObjectProvider<LeaveQueryService> leaveQueryServiceProvider,
                        ObjectProvider<AttendanceQueryService> attendanceQueryServiceProvider,
                        ObjectProvider<KnowledgeSearchService> knowledgeSearchServiceProvider) {
        this.agentRepository = agentRepository;
        this.objectMapper = objectMapper;
        this.orgQueryService = orgQueryService;
        this.leaveQueryServiceProvider = leaveQueryServiceProvider;
        this.attendanceQueryServiceProvider = attendanceQueryServiceProvider;
        this.knowledgeSearchServiceProvider = knowledgeSearchServiceProvider;
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
            String responsePayload = executeTool(call.binding(), call.requestPayloadJson());
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

    private String executeTool(AgentToolBindingRow binding, String requestPayloadJson) {
        if ("CONNECTOR".equals(binding.toolType())) {
            throw new BizException(501, "连接器工具执行器尚未接入");
        }
        JsonNode payload = readJson(requestPayloadJson);
        Object result = switch (binding.toolCode()) {
            case "ORG_GET_USER_SUMMARY" -> orgQueryService.getUserSummary(requiredLong(payload, "userId"));
            case "ORG_GET_DEPT_SUMMARY" -> orgQueryService.getDeptSummary(requiredLong(payload, "deptId"));
            case "ORG_GET_MANAGER_USER_ID" -> Map.of("managerUserId", orgQueryService.getManagerUserId(requiredLong(payload, "userId")));
            case "LEAVE_GET_BALANCES" -> requireLeaveService().getLeaveBalances(requiredLong(payload, "userId"));
            case "LEAVE_GET_AVAILABLE_MINUTES" -> Map.of(
                "availableMinutes",
                requireLeaveService().getAvailableMinutes(requiredLong(payload, "userId"), requiredLong(payload, "leaveTypeId"))
            );
            case "ATTENDANCE_GET_DAILY_RESULT" -> requireAttendanceService().getDailyResult(
                requiredLong(payload, "userId"),
                LocalDate.parse(requiredText(payload, "workDate"))
            );
            case "ATTENDANCE_GET_MONTHLY_EXCEPTIONS" -> requireAttendanceService().getMonthlyExceptions(
                requiredLong(payload, "userId"),
                requiredText(payload, "yearMonth")
            );
            case "KNOWLEDGE_SEARCH_CHUNKS" -> requireKnowledgeSearchService().searchChunks(
                requiredLong(payload, "spaceId"),
                requiredText(payload, "query"),
                optionalInt(payload, "topK", 5)
            );
            default -> throw new BizException(400, "暂不支持该SPI工具执行");
        };
        return writeJson(Map.of("status", "SUCCESS", "result", result == null ? Map.of() : result));
    }

    private LeaveQueryService requireLeaveService() {
        LeaveQueryService service = leaveQueryServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BizException(501, "假期查询工具未接入");
        }
        return service;
    }

    private AttendanceQueryService requireAttendanceService() {
        AttendanceQueryService service = attendanceQueryServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BizException(501, "考勤查询工具未接入");
        }
        return service;
    }

    private KnowledgeSearchService requireKnowledgeSearchService() {
        KnowledgeSearchService service = knowledgeSearchServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BizException(501, "知识库检索工具未接入");
        }
        return service;
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(StringUtils.hasText(value) ? value : "{}");
        } catch (Exception e) {
            throw new BizException(400, "JSON解析失败");
        }
    }

    private Long requiredLong(JsonNode payload, String fieldName) {
        JsonNode node = payload.get(fieldName);
        if (node == null || !node.canConvertToLong()) {
            throw new BizException(400, fieldName + "不能为空");
        }
        return node.asLong();
    }

    private String requiredText(JsonNode payload, String fieldName) {
        JsonNode node = payload.get(fieldName);
        if (node == null || !StringUtils.hasText(node.asText())) {
            throw new BizException(400, fieldName + "不能为空");
        }
        return node.asText().trim();
    }

    private int optionalInt(JsonNode payload, String fieldName, int defaultValue) {
        JsonNode node = payload.get(fieldName);
        return node == null || !node.canConvertToInt() ? defaultValue : node.asInt();
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
