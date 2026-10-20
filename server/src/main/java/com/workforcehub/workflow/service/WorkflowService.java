package com.workforcehub.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workforcehub.common.event.DomainEventPublisher;
import com.workforcehub.common.event.workflow.WorkflowCompletedEvent;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.attendance.service.AttendanceService;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.file.dto.CreateDownloadTicketRequest;
import com.workforcehub.file.dto.DownloadFileResult;
import com.workforcehub.file.dto.DownloadTicketResponse;
import com.workforcehub.file.repository.DownloadAttachmentRow;
import com.workforcehub.file.service.DownloadTicketExecutionService;
import com.workforcehub.file.storage.StorageObjectResource;
import com.workforcehub.file.storage.StorageProviderRegistry;
import com.workforcehub.leave.service.LeaveService;
import com.workforcehub.org.dto.OrgUserDetailResponse;
import com.workforcehub.org.service.OrgDirectoryService;
import com.workforcehub.workflow.dto.ClockCorrectionRequestCreateRequest;
import com.workforcehub.workflow.dto.GenericWorkflowRequestCreateRequest;
import com.workforcehub.workflow.dto.LeaveRequestCreateRequest;
import com.workforcehub.workflow.dto.WorkflowClockCorrectionDetailResponse;
import com.workforcehub.workflow.dto.WorkflowGenericDetailResponse;
import com.workforcehub.workflow.dto.WorkflowLeaveDetailResponse;
import com.workforcehub.workflow.dto.WorkflowRequestCreateResponse;
import com.workforcehub.workflow.dto.WorkflowRequestAttachmentResponse;
import com.workforcehub.workflow.dto.WorkflowRequestDetailResponse;
import com.workforcehub.workflow.dto.WorkflowRequestListQuery;
import com.workforcehub.workflow.dto.WorkflowRequestStatusResponse;
import com.workforcehub.workflow.dto.WorkflowRequestSummaryResponse;
import com.workforcehub.workflow.dto.WorkflowRequestWithdrawRequest;
import com.workforcehub.workflow.dto.WorkflowTaskApproveRequest;
import com.workforcehub.workflow.dto.WorkflowTaskActionResponse;
import com.workforcehub.workflow.dto.WorkflowTaskRejectRequest;
import com.workforcehub.workflow.dto.WorkflowAiReviewSummaryResponse;
import com.workforcehub.workflow.dto.WorkflowCcResponse;
import com.workforcehub.workflow.dto.WorkflowRequestStatResponse;
import com.workforcehub.workflow.dto.WorkflowTodoResponse;
import com.workforcehub.workflow.repository.WorkflowAttachmentRow;
import com.workforcehub.workflow.repository.WorkflowClockCorrectionDetailRow;
import com.workforcehub.workflow.repository.WorkflowGenericDetailRow;
import com.workforcehub.workflow.repository.WorkflowLeaveDetailRow;
import com.workforcehub.workflow.repository.WorkflowRepository;
import com.workforcehub.workflow.repository.WorkflowRequestDetailRow;
import com.workforcehub.workflow.repository.WorkflowRequestRow;
import com.workforcehub.workflow.repository.WorkflowRequestSummaryRow;
import com.workforcehub.workflow.repository.WorkflowTaskActionRow;
import com.workforcehub.workflow.repository.WorkflowTemplateVersionRow;
import com.workforcehub.workflow.repository.WorkflowTodoRow;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

import static com.workforcehub.common.util.TextValues.blankToNull;

/**
 * module-workflow 第一版审批服务。
 */
@Service
@ConditionalOnProperty(prefix = "workforce.workflow", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowService implements WorkflowAgentResultCallback {

    private static final String LEAVE_PROCESS_CODE = "LEAVE";
    private static final String LEAVE_BIZ_TYPE = "LEAVE";
    private static final String CLOCK_CORRECTION_PROCESS_CODE = "CLOCK_CORRECTION";
    private static final String CLOCK_CORRECTION_BIZ_TYPE = "CLOCK_CORRECTION";
    private static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String STATUS_IN_APPROVAL = "IN_APPROVAL";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String PUNCH_TYPE_ON_DUTY = "ON_DUTY";
    private static final String PUNCH_TYPE_OFF_DUTY = "OFF_DUTY";
    private static final List<String> GENERIC_PROCESS_CODES = List.of(
        "EXPENSE",
        "BUSINESS_TRIP",
        "OVERTIME",
        "SEAL",
        "PURCHASE",
        "CERTIFICATE",
        "BORROW",
        "PROJECT"
    );
    private static final long HALF_DAY_MINUTES = 240L;
    private static final LocalTime WORK_START = LocalTime.of(9, 0);
    private static final LocalTime LUNCH_BREAK_START = LocalTime.of(12, 0);
    private static final LocalTime LUNCH_BREAK_END = LocalTime.of(13, 0);
    private static final LocalTime WORK_END = LocalTime.of(18, 0);
    private static final DateTimeFormatter REQUEST_NO_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final WorkflowRepository workflowRepository;
    private final OrgDirectoryService orgDirectoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final StorageProviderRegistry storageProviderRegistry;
    private final DownloadTicketExecutionService downloadTicketExecutionService;
    private final LeaveService leaveService;
    private final AttendanceService attendanceService;
    private final ObjectProvider<WorkflowAgentPreReviewTrigger> agentPreReviewTriggerProvider;
    private final ObjectMapper objectMapper;
    private final DomainEventPublisher domainEventPublisher;

    public WorkflowService(WorkflowRepository workflowRepository,
                           OrgDirectoryService orgDirectoryService,
                           RuntimeService runtimeService,
                           TaskService taskService,
                           StorageProviderRegistry storageProviderRegistry,
                           DownloadTicketExecutionService downloadTicketExecutionService,
                           LeaveService leaveService,
                           AttendanceService attendanceService,
                           ObjectProvider<WorkflowAgentPreReviewTrigger> agentPreReviewTriggerProvider,
                           ObjectMapper objectMapper,
                           DomainEventPublisher domainEventPublisher) {
        this.workflowRepository = workflowRepository;
        this.orgDirectoryService = orgDirectoryService;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.storageProviderRegistry = storageProviderRegistry;
        this.downloadTicketExecutionService = downloadTicketExecutionService;
        this.leaveService = leaveService;
        this.attendanceService = attendanceService;
        this.agentPreReviewTriggerProvider = agentPreReviewTriggerProvider;
        this.objectMapper = objectMapper;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowRequestCreateResponse submitLeaveRequest(LeaveRequestCreateRequest request) {
        Long applicantUserId = CurrentUserHolder.requireUserId();
        validateLeaveRequest(request);
        OrgUserDetailResponse applicant = orgDirectoryService.getUserDetail(applicantUserId);
        if (applicant.getManagerUserId() == null) {
            throw new BizException(400, "当前员工没有直属上级，无法发起请假审批");
        }

        WorkflowTemplateVersionRow templateVersion =
            workflowRepository.getPublishedTemplateVersion(LEAVE_PROCESS_CODE);
        String title = applicant.getRealName() + "的请假申请";
        String requestNo = buildRequestNo();
        Long requestId = IdGenerator.nextId();
        Long leaveDetailId = IdGenerator.nextId();
        workflowRepository.insertWorkflowRequest(
            requestId,
            requestNo,
            LEAVE_BIZ_TYPE,
            leaveDetailId,
            applicantUserId,
            applicant.getPrimaryDeptId(),
            applicant.getPositionId(),
            title,
            templateVersion.id()
        );
        workflowRepository.insertLeaveDetail(leaveDetailId, requestId, request);
        bindAttachments(requestId, applicantUserId, request.getAttachmentRefIds());
        // 提交时立即冻结余额，防止审批期间重复请假导致超领
        leaveService.freezeForWorkflowSubmission(
            applicantUserId,
            request.getLeaveTypeId(),
            request.getDurationMinutes(),
            requestId,
            applicantUserId
        );

        // 有 AI 预审配置时先进入 AI 审核，审核通过后再流转到人工审批
        Long agentTaskId = triggerAgentPreReview(requestId, LEAVE_PROCESS_CODE, LEAVE_BIZ_TYPE, leaveDetailId, applicantUserId);
        if (agentTaskId != null) {
            workflowRepository.markPendingAgentPreReview(requestId, agentTaskId, applicantUserId);
            WorkflowRequestCreateResponse response = new WorkflowRequestCreateResponse();
            response.setRequestId(requestId);
            response.setRequestNo(requestNo);
            response.setStatus(STATUS_PENDING_REVIEW);
            response.setStatusName(statusName(STATUS_PENDING_REVIEW));
            response.setAgentTaskId(agentTaskId);
            return response;
        }

        String processInstanceId = startHumanApproval(
            requestId,
            title,
            request.getReason(),
            applicant,
            templateVersion,
            applicantUserId
        );

        WorkflowRequestCreateResponse response = new WorkflowRequestCreateResponse();
        response.setRequestId(requestId);
        response.setRequestNo(requestNo);
        response.setStatus(STATUS_IN_APPROVAL);
        response.setStatusName(statusName(STATUS_IN_APPROVAL));
        response.setFlowableProcessInstanceId(processInstanceId);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowRequestCreateResponse submitClockCorrectionRequest(ClockCorrectionRequestCreateRequest request) {
        Long applicantUserId = CurrentUserHolder.requireUserId();
        String missingPunchType = normalizePunchType(request.getMissingPunchType());
        validateClockCorrectionRequest(request);
        OrgUserDetailResponse applicant = orgDirectoryService.getUserDetail(applicantUserId);
        if (applicant.getManagerUserId() == null) {
            throw new BizException(400, "当前员工没有直属上级，无法发起补卡审批");
        }

        WorkflowTemplateVersionRow templateVersion =
            workflowRepository.getPublishedTemplateVersion(CLOCK_CORRECTION_PROCESS_CODE);
        String title = applicant.getRealName() + "的补卡申请";
        String requestNo = buildRequestNo(CLOCK_CORRECTION_BIZ_TYPE);
        Long requestId = IdGenerator.nextId();
        Long detailId = IdGenerator.nextId();
        workflowRepository.insertWorkflowRequest(
            requestId,
            requestNo,
            CLOCK_CORRECTION_BIZ_TYPE,
            detailId,
            applicantUserId,
            applicant.getPrimaryDeptId(),
            applicant.getPositionId(),
            title,
            templateVersion.id()
        );
        request.setMissingPunchType(missingPunchType);
        workflowRepository.insertClockCorrectionDetail(detailId, requestId, request);
        bindAttachments(requestId, applicantUserId, request.getAttachmentRefIds());

        String processInstanceId = startHumanApproval(
            requestId,
            title,
            request.getReason(),
            applicant,
            templateVersion,
            applicantUserId
        );

        WorkflowRequestCreateResponse response = new WorkflowRequestCreateResponse();
        response.setRequestId(requestId);
        response.setRequestNo(requestNo);
        response.setStatus(STATUS_IN_APPROVAL);
        response.setStatusName(statusName(STATUS_IN_APPROVAL));
        response.setFlowableProcessInstanceId(processInstanceId);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowRequestCreateResponse submitGenericRequest(GenericWorkflowRequestCreateRequest request) {
        Long applicantUserId = CurrentUserHolder.requireUserId();
        String processCode = normalizeGenericProcessCode(request.getProcessCode());
        validateGenericRequest(request);
        OrgUserDetailResponse applicant = orgDirectoryService.getUserDetail(applicantUserId);
        if (applicant.getManagerUserId() == null) {
            throw new BizException(400, "当前员工没有直属上级，无法发起审批");
        }

        WorkflowTemplateVersionRow templateVersion =
            workflowRepository.getPublishedTemplateVersion(processCode);
        String title = buildGenericTitle(applicant, processCode, request.getTitle());
        String requestNo = buildRequestNo(processCode);
        Long requestId = IdGenerator.nextId();
        Long detailId = IdGenerator.nextId();
        workflowRepository.insertWorkflowRequest(
            requestId,
            requestNo,
            processCode,
            detailId,
            applicantUserId,
            applicant.getPrimaryDeptId(),
            applicant.getPositionId(),
            title,
            templateVersion.id()
        );
        workflowRepository.insertGenericDetail(
            detailId,
            requestId,
            processCode,
            compactJsonPayload(request.getFormPayload()),
            request
        );
        bindAttachments(requestId, applicantUserId, request.getAttachmentRefIds());

        Long agentTaskId = triggerAgentPreReview(requestId, processCode, processCode, detailId, applicantUserId);
        if (agentTaskId != null) {
            workflowRepository.markPendingAgentPreReview(requestId, agentTaskId, applicantUserId);
            WorkflowRequestCreateResponse response = new WorkflowRequestCreateResponse();
            response.setRequestId(requestId);
            response.setRequestNo(requestNo);
            response.setStatus(STATUS_PENDING_REVIEW);
            response.setStatusName(statusName(STATUS_PENDING_REVIEW));
            response.setAgentTaskId(agentTaskId);
            return response;
        }

        String processInstanceId = startHumanApproval(
            requestId,
            title,
            blankToNull(request.getSummary()),
            applicant,
            templateVersion,
            applicantUserId
        );

        WorkflowRequestCreateResponse response = new WorkflowRequestCreateResponse();
        response.setRequestId(requestId);
        response.setRequestNo(requestNo);
        response.setStatus(STATUS_IN_APPROVAL);
        response.setStatusName(statusName(STATUS_IN_APPROVAL));
        response.setFlowableProcessInstanceId(processInstanceId);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onPreReviewApproved(Long requestId, Long agentTaskId, String decisionJson) {
        completeAgentPreReviewWithHumanApproval(requestId, agentTaskId, decisionJson);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onPreReviewRejected(Long requestId, Long agentTaskId, String decisionJson) {
        terminateAgentPreReview(requestId, agentTaskId, decisionJson);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onPreReviewTimeout(Long requestId, Long agentTaskId, String decisionJson) {
        terminateAgentPreReview(requestId, agentTaskId, decisionJson);
    }

    private void terminateAgentPreReview(Long requestId, Long agentTaskId, String decisionJson) {
        WorkflowRequestRow workflowRequest = workflowRepository.getRequestByIdForUpdateOrThrow(requestId);
        ensurePendingAgentPreReview(workflowRequest, agentTaskId);
        releaseFrozenLeaveIfNeeded(workflowRequest, STATUS_REJECTED, agentTaskId);
        workflowRepository.insertAgentPreReviewAction(
            requestId,
            agentTaskId,
            decisionJson,
            workflowRequest.status(),
            STATUS_REJECTED
        );
        workflowRepository.finishRequest(requestId, STATUS_REJECTED, agentTaskId);
        workflowRepository.closeAllPendingTodos(requestId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onPreReviewToHuman(Long requestId, Long agentTaskId, String decisionJson) {
        completeAgentPreReviewWithHumanApproval(requestId, agentTaskId, decisionJson);
    }

    private void completeAgentPreReviewWithHumanApproval(Long requestId,
                                                         Long agentTaskId,
                                                         String decisionJson) {
        WorkflowRequestRow workflowRequest = workflowRepository.getRequestByIdForUpdateOrThrow(requestId);
        ensurePendingAgentPreReview(workflowRequest, agentTaskId);
        workflowRepository.insertAgentPreReviewAction(
            requestId,
            agentTaskId,
            decisionJson,
            workflowRequest.status(),
            STATUS_IN_APPROVAL
        );

        OrgUserDetailResponse applicant = orgDirectoryService.getUserDetail(workflowRequest.applicantUserId());
        if (applicant.getManagerUserId() == null) {
            throw new BizException(400, "当前员工没有直属上级，无法发起请假审批");
        }
        WorkflowTemplateVersionRow templateVersion = workflowRepository.getTemplateVersionByRequestId(requestId);
        WorkflowLeaveDetailRow leaveDetail = workflowRepository.getLeaveDetailByRequestId(requestId);
        WorkflowClockCorrectionDetailRow clockCorrection =
            workflowRepository.getClockCorrectionDetailByRequestId(requestId);
        WorkflowGenericDetailRow generic = workflowRepository.getGenericDetailByRequestId(requestId);
        startHumanApproval(
            requestId,
            workflowRequest.title(),
            requestSummary(leaveDetail, clockCorrection, generic),
            applicant,
            templateVersion,
            agentTaskId
        );
    }

    private Long triggerAgentPreReview(Long requestId,
                                       String processTypeCode,
                                       String bizType,
                                       Long bizId,
                                       Long applicantUserId) {
        WorkflowAgentPreReviewTrigger trigger = agentPreReviewTriggerProvider.getIfAvailable();
        if (trigger == null) {
            return null;
        }
        return trigger.triggerPreReview(
            requestId,
            processTypeCode,
            bizType,
            bizId,
            applicantUserId
        );
    }

    // 启动 Flowable 人工审批流程，同时在业务表插入待办记录
    private String startHumanApproval(Long requestId,
                                      String title,
                                      String summary,
                                      OrgUserDetailResponse applicant,
                                      WorkflowTemplateVersionRow templateVersion,
                                      Long operatorId) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", requestId);
        variables.put("applicantUserId", String.valueOf(applicant.getUserId()));
        variables.put("managerUserId", String.valueOf(applicant.getManagerUserId()));
        // 优先用 definitionId（精确版本），降级用 definitionKey（兜底最新版本）
        ProcessInstance processInstance = StringUtils.hasText(templateVersion.flowableDefinitionId())
            ? runtimeService.startProcessInstanceById(
                templateVersion.flowableDefinitionId(),
                String.valueOf(requestId),
                variables
            )
            : runtimeService.startProcessInstanceByKey(
                templateVersion.flowableDefinitionKey(),
                String.valueOf(requestId),
                variables
            );
        int updated = workflowRepository.markHumanApprovalStarted(requestId, processInstance.getProcessInstanceId(), operatorId);
        if (updated == 0) {
            throw new BizException(409, "审批申请已被其他事务处理");
        }

        // Flowable 的任务 assignee 用 userId 字符串，这里查询确认任务已生成
        Task managerTask = taskService.createTaskQuery()
            .processInstanceId(processInstance.getProcessInstanceId())
            .taskAssignee(String.valueOf(applicant.getManagerUserId()))
            .singleResult();
        if (managerTask == null) {
            throw new BizException(500, "Flowable 未生成直属上级审批任务");
        }
        workflowRepository.insertTodo(
            requestId,
            applicant.getManagerUserId(),
            managerTask.getId(),
            title,
            summary
        );
        return processInstance.getProcessInstanceId();
    }

    private void ensurePendingAgentPreReview(WorkflowRequestRow workflowRequest, Long agentTaskId) {
        if (!STATUS_PENDING_REVIEW.equals(workflowRequest.status())) {
            throw new BizException(400, "当前申请状态不允许处理AI预审结果");
        }
        if (workflowRequest.flowableProcessInstanceId() != null) {
            throw new BizException(409, "AI预审完成前不应存在Flowable流程实例");
        }
        if (agentTaskId == null || !agentTaskId.equals(workflowRequest.agentPreReviewTaskId())) {
            throw new BizException(409, "AI预审任务不匹配");
        }
    }

    public List<WorkflowTodoResponse> listCurrentEmployeeTodos() {
        Long ownerUserId = CurrentUserHolder.requireUserId();
        List<WorkflowTodoRow> rows = workflowRepository.listPendingTodos(ownerUserId);
        // 接口需求表 #6：批量取 AI 预审结论
        Map<Long, String> decisions = workflowRepository.findLatestPreReviewDecisions(
            rows.stream().map(WorkflowTodoRow::requestId).collect(Collectors.toSet()));
        return rows.stream()
            .map(row -> {
                WorkflowTodoResponse response = toTodoResponse(row);
                String decision = decisions.get(row.requestId());
                if (decision != null) {
                    response.setAiVerdict(mapAiVerdict(decision));
                    response.setAiVerdictName(aiVerdictName(response.getAiVerdict()));
                }
                return response;
            })
            .toList();
    }




    /** 接口需求表 #8：我发起的审批统计 */
    @Transactional(readOnly = true)
    public WorkflowRequestStatResponse myRequestSummary() {
        Long userId = CurrentUserHolder.requireUserId();
        var row = workflowRepository.countMyRequestSummary(userId);
        WorkflowRequestStatResponse response = new WorkflowRequestStatResponse();
        response.setInFlight(row.inFlight());
        response.setFinishedThisMonth(row.finishedThisMonth());
        response.setAvgHandleHours(row.avgHandleHours() == null ? null : Math.round(row.avgHandleHours() * 10) / 10.0);
        return response;
    }

    /** 接口需求表 #7：分页查抄送我的 */
    public PageResult<WorkflowCcResponse> pageCcToMe(long pageNo, long pageSize) {
        Long userId = CurrentUserHolder.requireUserId();
        long total = workflowRepository.countCcToMe(userId);
        List<WorkflowCcResponse> rows = workflowRepository.pageCcToMe(userId, (int) pageNo, (int) pageSize).stream()
            .map(row -> {
                WorkflowCcResponse response = new WorkflowCcResponse();
                response.setRequestId(String.valueOf(row.requestId()));
                response.setRequestNo(row.requestNo());
                response.setBizTypeName(row.bizTypeName());
                response.setTitle(row.title());
                response.setApplicantName(row.applicantName());
                response.setCcAt(row.ccAt());
                response.setStatus(row.status());
                response.setStatusName(row.statusName());
                response.setRead(row.read());
                return response;
            })
            .toList();
        return PageResult.of(pageNo, pageSize, total, rows);
    }

    /** 接口需求表 #7：标记抄送已读 */
    @Transactional(rollbackFor = Exception.class)
    public void markCcRead(String requestId) {
        Long userId = CurrentUserHolder.requireUserId();
        workflowRepository.markCcRead(userId, Long.valueOf(requestId));
    }

    /** 接口需求表 #6 期望A：本周 AI 预审统计 */
    public WorkflowAiReviewSummaryResponse aiReviewSummary() {
        Long userId = CurrentUserHolder.requireUserId();
        LocalDate weekStart = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1L);
        Map<String, Long> distribution = workflowRepository.countMyPreReviewDecisions(userId, weekStart);
        long reviewed = distribution.values().stream().mapToLong(Long::longValue).sum();
        long pass = distribution.getOrDefault("APPROVED", 0L);
        long blocked = distribution.getOrDefault("REJECTED", 0L);
        WorkflowAiReviewSummaryResponse response = new WorkflowAiReviewSummaryResponse();
        response.setWeekReviewed(reviewed);
        response.setPassRate(reviewed == 0 ? null : Math.round(pass * 100.0 / reviewed) / 100.0);
        response.setBlockedCount(blocked);
        response.setBlockedRules(List.of());
        return response;
    }

    private String mapAiVerdict(String decision) {
        return switch (decision) {
            case "APPROVED" -> "PASS";
            case "REJECTED" -> "BLOCKED";
            default -> "REVIEWING";
        };
    }

    private String aiVerdictName(String verdict) {
        return switch (verdict) {
            case "PASS" -> "AI 预审通过";
            case "BLOCKED" -> "预审未通过";
            default -> "AI 预审中";
        };
    }

    public PageResult<WorkflowRequestSummaryResponse> pageCurrentEmployeeRequests(WorkflowRequestListQuery query) {
        Long applicantUserId = CurrentUserHolder.requireUserId();
        long total = workflowRepository.countRequests(applicantUserId, query);
        List<WorkflowRequestSummaryResponse> rows = workflowRepository.pageRequests(applicantUserId, query).stream()
            .map(this::toRequestSummaryResponse)
            .toList();
        return PageResult.of(query.getPageNo(), query.getPageSize(), total, rows);
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowRequestStatusResponse approveTask(String taskId, WorkflowTaskApproveRequest request) {
        return completeManagerTask(taskId, request.getComment(), "APPROVE", "APPROVED");
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowRequestStatusResponse rejectTask(String taskId, WorkflowTaskRejectRequest request) {
        return completeManagerTask(taskId, request.getComment(), "REJECT", "REJECTED");
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowRequestStatusResponse withdrawRequest(Long requestId, WorkflowRequestWithdrawRequest request) {
        Long applicantUserId = CurrentUserHolder.requireUserId();
        WorkflowRequestRow workflowRequest = workflowRepository.getRequestByIdOrThrow(requestId);
        if (!applicantUserId.equals(workflowRequest.applicantUserId())) {
            throw new BizException(403, "只有申请人可以撤回该申请");
        }
        if (!STATUS_IN_APPROVAL.equals(workflowRequest.status())) {
            throw new BizException(400, "当前申请状态不允许撤回");
        }
        // 撤回时释放之前冻结的假期余额
        releaseFrozenLeaveIfNeeded(workflowRequest, "WITHDRAWN", applicantUserId);

        workflowRepository.insertTaskAction(
            workflowRequest.id(),
            null,
            applicantUserId,
            "WITHDRAW",
            request.getComment(),
            workflowRequest.status(),
            "WITHDRAWN"
        );
        runtimeService.deleteProcessInstance(workflowRequest.flowableProcessInstanceId(), "申请人撤回");
        workflowRepository.finishRequest(workflowRequest.id(), "WITHDRAWN", applicantUserId);
        workflowRepository.closeAllPendingTodos(workflowRequest.id());
        return statusResponse(workflowRequest.id(), "WITHDRAWN");
    }

    public WorkflowRequestDetailResponse getRequestDetail(Long requestId) {
        Long userId = CurrentUserHolder.requireUserId();
        ensureRequestParticipant(requestId, userId);
        WorkflowRequestDetailRow row = workflowRepository.getRequestDetailOrThrow(requestId);
        WorkflowLeaveDetailRow leave = workflowRepository.getLeaveDetailByRequestId(requestId);
        WorkflowClockCorrectionDetailRow clockCorrection = workflowRepository.getClockCorrectionDetailByRequestId(requestId);
        WorkflowGenericDetailRow generic = workflowRepository.getGenericDetailByRequestId(requestId);
        List<WorkflowAttachmentRow> attachments = workflowRepository.listAttachmentsByRequestId(requestId);
        return toRequestDetailResponse(row, leave, clockCorrection, generic, attachments);
    }

    public List<WorkflowTaskActionResponse> listRequestActions(Long requestId) {
        Long userId = CurrentUserHolder.requireUserId();
        ensureRequestParticipant(requestId, userId);
        return workflowRepository.listTaskActions(requestId).stream()
            .map(this::toActionResponse)
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public DownloadTicketResponse createAttachmentDownloadTicket(Long requestId,
                                                                Long attachmentRefId,
                                                                CreateDownloadTicketRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        ensureRequestParticipant(requestId, userId);
        if (!"SINGLE".equalsIgnoreCase(request.getDownloadType())) {
            throw new BizException(400, "第一版仅支持单文件下载");
        }

        WorkflowAttachmentRow attachment = workflowRepository.getWorkflowAttachmentOrThrow(requestId, attachmentRefId);
        ensureDownloadableWorkflowAttachment(attachment);
        Long ticketId = IdGenerator.nextId();
        String ticketNo = "WFDL" + ticketId;
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(10);
        String downloadUrl = "/api/employee/workflow/attachments/download/" + ticketNo;
        workflowRepository.insertDownloadTicket(
            ticketId,
            ticketNo,
            attachmentRefId,
            userId,
            downloadUrl,
            blankToNull(request.getReason()),
            expireAt
        );
        workflowRepository.insertAudit(
            "USER",
            userId,
            "WORKFLOW_ATTACHMENT_DOWNLOAD_TICKET_CREATED",
            "DOWNLOAD_TICKET",
            ticketId,
            "SUCCESS",
            "{\"requestId\":%s,\"attachmentRefId\":%s}".formatted(requestId, attachmentRefId)
        );

        DownloadTicketResponse response = new DownloadTicketResponse();
        response.setDownloadTicketId(ticketId);
        response.setTicketNo(ticketNo);
        response.setDownloadUrl(downloadUrl);
        response.setExpireAt(expireAt);
        response.setFileName(attachment.displayName());
        response.setFileSize(attachment.fileSize());
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public DownloadFileResult downloadAttachment(String ticketNo) {
        Long userId = CurrentUserHolder.requireUserId();
        WorkflowRepository.WorkflowDownloadTicketRow ticket = workflowRepository.getWorkflowDownloadTicketOrThrow(ticketNo);
        if (!"USER".equals(ticket.requesterType()) || !userId.equals(ticket.requesterId())) {
            throw new BizException(403, "无权使用该下载票据");
        }
        if (!"ACTIVE".equals(ticket.status())) {
            throw new BizException(409, "下载票据已使用或已失效");
        }
        if (ticket.expireAt().isBefore(LocalDateTime.now())) {
            downloadTicketExecutionService.expireActiveTicket(ticket.id());
            throw new BizException(410, "下载链接已过期，请重新申请");
        }

        WorkflowRepository.WorkflowAttachmentDownloadScopeRow scope =
            workflowRepository.getWorkflowAttachmentDownloadScopeOrThrow(ticket.attachmentRefId());
        ensureRequestParticipant(scope.requestId(), userId);
        DownloadAttachmentRow attachment = workflowRepository.getDownloadAttachmentOrThrow(ticket.attachmentRefId());
        ensureDownloadableWorkflowAttachment(attachment);

        downloadTicketExecutionService.claimActiveTicket(ticket.id());
        StorageObjectResource resource = storageProviderRegistry.require(attachment.storageProvider())
            .loadObject(attachment.bucketName(), attachment.objectKey());
        workflowRepository.insertAudit(
            "USER",
            userId,
            "WORKFLOW_ATTACHMENT_DOWNLOAD_EXECUTED",
            "DOWNLOAD_TICKET",
            ticket.id(),
            "SUCCESS",
            "{\"requestId\":%s,\"attachmentRefId\":%s}".formatted(scope.requestId(), ticket.attachmentRefId())
        );
        return new DownloadFileResult(resource.resource(), attachment.displayName(), attachment.mimeType(), resource.size());
    }

    // 审批通过或驳回的统一处理：扣减/释放余额、推进 Flowable 流程、更新业务状态
    private WorkflowRequestStatusResponse completeManagerTask(String taskId,
                                                              String comment,
                                                              String actionType,
                                                              String afterStatus) {
        Long approverUserId = CurrentUserHolder.requireUserId();
        WorkflowRequestRow workflowRequest = workflowRepository.getRequestByTaskIdForUpdateOrThrow(taskId);
        Task task = taskService.createTaskQuery()
            .taskId(taskId)
            .taskAssignee(String.valueOf(approverUserId))
            .singleResult();
        if (task == null) {
            throw new BizException(403, "当前用户不是该审批任务处理人");
        }
        if (!STATUS_IN_APPROVAL.equals(workflowRequest.status())) {
            throw new BizException(400, "当前申请状态不允许审批");
        }

        WorkflowLeaveDetailRow approvedLeaveDetail = null;
        WorkflowClockCorrectionDetailRow approvedClockCorrectionDetail = null;
        if ("APPROVE".equals(actionType)) {
            // 通过时：请假扣减冻结余额，补卡后续写入考勤记录
            if (LEAVE_BIZ_TYPE.equals(workflowRequest.bizType())) {
                WorkflowLeaveDetailRow leaveDetail = workflowRepository.getLeaveDetailByRequestId(workflowRequest.id());
                if (leaveDetail == null) {
                    throw new BizException(404, "请假申请明细不存在");
                }
                approvedLeaveDetail = leaveDetail;
                leaveService.consumeFrozenForWorkflowApproval(
                    workflowRequest.applicantUserId(),
                    leaveDetail.leaveTypeId(),
                    leaveDetail.durationMinutes(),
                    workflowRequest.id(),
                    approverUserId
                );
            } else if (CLOCK_CORRECTION_BIZ_TYPE.equals(workflowRequest.bizType())) {
                WorkflowClockCorrectionDetailRow clockCorrectionDetail =
                    workflowRepository.getClockCorrectionDetailByRequestId(workflowRequest.id());
                if (clockCorrectionDetail == null) {
                    throw new BizException(404, "补卡申请明细不存在");
                }
                approvedClockCorrectionDetail = clockCorrectionDetail;
            }
        } else if ("REJECT".equals(actionType) && LEAVE_BIZ_TYPE.equals(workflowRequest.bizType())) {
            // 驳回时释放冻结余额
            releaseFrozenLeaveIfNeeded(workflowRequest, afterStatus, approverUserId);
        }
        workflowRepository.insertTaskAction(
            workflowRequest.id(),
            taskId,
            approverUserId,
            actionType,
            comment,
            workflowRequest.status(),
            afterStatus
        );
        taskService.complete(taskId);
        workflowRepository.finishRequest(workflowRequest.id(), afterStatus, approverUserId);
        workflowRepository.closePendingTodo(workflowRequest.id(), approverUserId);
        // 请假通过后重新计算考勤结果
        if (approvedLeaveDetail != null) {
            recalculateAttendanceForApprovedLeave(workflowRequest.applicantUserId(), approvedLeaveDetail);
        }
        // 补卡通过后写入考勤打卡记录
        if (approvedClockCorrectionDetail != null) {
            attendanceService.applyApprovedClockCorrection(
                workflowRequest.applicantUserId(),
                approvedClockCorrectionDetail.targetDate(),
                approvedClockCorrectionDetail.missingPunchType(),
                approvedClockCorrectionDetail.expectedPunchAt(),
                workflowRequest.id(),
                approverUserId
            );
        }
        WorkflowRequestStatusResponse response = statusResponse(workflowRequest.id(), afterStatus);
        domainEventPublisher.publishAfterCommit(new WorkflowCompletedEvent(
            workflowRequest.id(),
            workflowRequest.requestNo(),
            workflowRequest.bizType(),
            workflowRequest.applicantUserId(),
            taskId,
            workflowRequest.status(),
            afterStatus,
            approverUserId,
            LocalDateTime.now()
        ));
        return response;
    }

    // 只有请假类型需要释放冻结余额，补卡/其他类型直接跳过
    private void releaseFrozenLeaveIfNeeded(WorkflowRequestRow workflowRequest, String terminalStatus, Long operatorId) {
        if (!LEAVE_BIZ_TYPE.equals(workflowRequest.bizType())) {
            return;
        }
        WorkflowLeaveDetailRow leaveDetail = workflowRepository.getLeaveDetailByRequestId(workflowRequest.id());
        if (leaveDetail == null) {
            throw new BizException(404, "请假申请明细不存在");
        }
        leaveService.releaseFrozenForWorkflowTerminal(
            workflowRequest.applicantUserId(),
            leaveDetail.leaveTypeId(),
            leaveDetail.durationMinutes(),
            workflowRequest.id(),
            terminalStatus,
            operatorId
        );
    }

    // 请假审批通过后，重新计算请假期间每天的考勤结果
    private void recalculateAttendanceForApprovedLeave(Long userId, WorkflowLeaveDetailRow leaveDetail) {
        LocalDate workDate = leaveDetail.startAt().toLocalDate();
        LocalDate endDate = leaveDetail.endAt().toLocalDate();
        while (!workDate.isAfter(endDate)) {
            attendanceService.recalculateDailyResultForExternalEvent(userId, workDate);
            workDate = workDate.plusDays(1);
        }
    }

    private void ensureRequestParticipant(Long requestId, Long userId) {
        if (!workflowRepository.isRequestParticipant(requestId, userId)) {
            throw new BizException(403, "无权查看该审批申请");
        }
    }

    private void bindAttachments(Long requestId, Long applicantUserId, List<Long> attachmentRefIds) {
        List<Long> distinctAttachmentRefIds = new LinkedHashSet<>(
            attachmentRefIds == null ? List.<Long>of() : attachmentRefIds
        ).stream().toList();
        int sortOrder = 0;
        for (Long attachmentRefId : distinctAttachmentRefIds) {
            WorkflowAttachmentRow attachment = workflowRepository.getAttachmentByIdOrThrow(attachmentRefId);
            ensureUsablePersonalAttachment(attachment, applicantUserId);
            Long workflowAttachmentRefId = workflowRepository.createWorkflowAttachmentRef(
                requestId,
                attachment,
                applicantUserId
            );
            workflowRepository.insertRequestAttachment(requestId, workflowAttachmentRefId, sortOrder++, applicantUserId);
        }
    }

    private void ensureUsablePersonalAttachment(WorkflowAttachmentRow attachment, Long applicantUserId) {
        if (!"USER".equals(attachment.uploaderType()) || !applicantUserId.equals(attachment.uploaderId())) {
            throw new BizException(403, "无权使用该附件引用");
        }
        if (!"PERSONAL_FILE".equals(attachment.ownerType())) {
            throw new BizException(403, "无权使用该附件引用");
        }
        if (attachment.deletedAt() != null || attachment.attachmentDeleted() != 0 || attachment.fileDeleted() != 0) {
            throw new BizException(404, "附件引用不存在");
        }
        if ("FAILED".equals(attachment.scanStatus())) {
            throw new BizException(409, "附件安全扫描未通过");
        }
    }

    private void ensureDownloadableWorkflowAttachment(WorkflowAttachmentRow attachment) {
        if (!"WF_REQUEST".equals(attachment.ownerType())) {
            throw new BizException(404, "审批附件不存在");
        }
        if (attachment.deletedAt() != null || attachment.attachmentDeleted() != 0 || attachment.fileDeleted() != 0) {
            throw new BizException(409, "审批附件已删除");
        }
        if ("FAILED".equals(attachment.scanStatus())) {
            throw new BizException(409, "附件安全扫描未通过");
        }
    }

    private void ensureDownloadableWorkflowAttachment(DownloadAttachmentRow attachment) {
        if (!"WF_REQUEST".equals(attachment.ownerType())) {
            throw new BizException(404, "审批附件不存在");
        }
        if (attachment.deletedAt() != null || attachment.attachmentDeleted() != 0 || attachment.fileDeleted() != 0) {
            throw new BizException(409, "审批附件已删除");
        }
        if ("FAILED".equals(attachment.scanStatus())) {
            throw new BizException(409, "附件安全扫描未通过");
        }
    }

    private void validateLeaveRequest(LeaveRequestCreateRequest request) {
        if (!request.getEndAt().isAfter(request.getStartAt())) {
            throw new BizException(400, "结束时间必须晚于开始时间");
        }
        long actualDurationMinutes = roundLeaveDurationMinutes(calculateLeaveDurationMinutes(request.getStartAt(), request.getEndAt()));
        if (actualDurationMinutes != request.getDurationMinutes()) {
            throw new BizException(400, "请假时长与开始结束时间不一致");
        }
        if (!workflowRepository.existsActiveLeaveType(request.getLeaveTypeId())) {
            throw new BizException(404, "假种不存在或已停用");
        }
        leaveService.ensureLeaveRequestBalance(
            CurrentUserHolder.requireUserId(),
            request.getLeaveTypeId(),
            request.getDurationMinutes()
        );
    }

    private void validateClockCorrectionRequest(ClockCorrectionRequestCreateRequest request) {
        if (!request.getTargetDate().equals(request.getExpectedPunchAt().toLocalDate())) {
            throw new BizException(400, "补卡时间必须落在补卡日期内");
        }
    }

    private void validateGenericRequest(GenericWorkflowRequestCreateRequest request) {
        if (request.getFormPayload() == null || !request.getFormPayload().isObject()) {
            throw new BizException(400, "通用OA表单数据格式错误");
        }
    }

    private long calculateLeaveDurationMinutes(LocalDateTime startAt, LocalDateTime endAt) {
        long durationMinutes = 0;
        LocalDate currentDate = startAt.toLocalDate();
        LocalDate endDate = endAt.toLocalDate();
        while (!currentDate.isAfter(endDate)) {
            durationMinutes += overlapMinutes(startAt, endAt, currentDate.atTime(WORK_START), currentDate.atTime(LUNCH_BREAK_START));
            durationMinutes += overlapMinutes(startAt, endAt, currentDate.atTime(LUNCH_BREAK_END), currentDate.atTime(WORK_END));
            currentDate = currentDate.plusDays(1);
        }
        return durationMinutes;
    }

    private long roundLeaveDurationMinutes(long durationMinutes) {
        if (durationMinutes <= 0) {
            return 0;
        }
        return ((durationMinutes + HALF_DAY_MINUTES - 1) / HALF_DAY_MINUTES) * HALF_DAY_MINUTES;
    }

    private long overlapMinutes(LocalDateTime rangeStart,
                                LocalDateTime rangeEnd,
                                LocalDateTime segmentStart,
                                LocalDateTime segmentEnd) {
        LocalDateTime overlapStart = rangeStart.isAfter(segmentStart) ? rangeStart : segmentStart;
        LocalDateTime overlapEnd = rangeEnd.isBefore(segmentEnd) ? rangeEnd : segmentEnd;
        if (!overlapEnd.isAfter(overlapStart)) {
            return 0;
        }
        return Duration.between(overlapStart, overlapEnd).toMinutes();
    }

    private WorkflowTodoResponse toTodoResponse(WorkflowTodoRow row) {
        WorkflowTodoResponse response = new WorkflowTodoResponse();
        response.setTodoId(row.todoId());
        response.setRequestId(row.requestId());
        response.setTaskId(row.taskId());
        response.setTitle(row.title());
        response.setSummary(row.summary());
        response.setStatus(row.status());
        response.setStatusName(statusName(row.status()));
        response.setPriority(row.priority());
        response.setPriorityName(priorityName(row.priority()));
        response.setCreatedAt(row.createdAt());
        return response;
    }

    private WorkflowRequestSummaryResponse toRequestSummaryResponse(WorkflowRequestSummaryRow row) {
        WorkflowRequestSummaryResponse response = new WorkflowRequestSummaryResponse();
        response.setRequestId(row.id());
        response.setRequestNo(row.requestNo());
        response.setBizType(row.bizType());
        response.setBizTypeName(bizTypeName(row.bizType()));
        response.setBizId(row.bizId());
        response.setTitle(row.title());
        response.setStatus(row.status());
        response.setStatusName(statusName(row.status()));
        response.setCurrentStepKey(row.currentStepKey());
        response.setCurrentStepName(row.currentStepName());
        response.setSubmittedAt(row.submittedAt());
        response.setFinishedAt(row.finishedAt());
        return response;
    }

    private WorkflowRequestDetailResponse toRequestDetailResponse(WorkflowRequestDetailRow row,
                                                                  WorkflowLeaveDetailRow leave,
                                                                  WorkflowClockCorrectionDetailRow clockCorrection,
                                                                  WorkflowGenericDetailRow generic,
                                                                  List<WorkflowAttachmentRow> attachments) {
        WorkflowRequestDetailResponse response = new WorkflowRequestDetailResponse();
        response.setRequestId(row.id());
        response.setRequestNo(row.requestNo());
        response.setBizType(row.bizType());
        response.setBizTypeName(bizTypeName(row.bizType()));
        response.setBizId(row.bizId());
        response.setApplicantUserId(row.applicantUserId());
        response.setApplicantName(row.applicantName());
        response.setApplicantEmployeeNo(row.applicantEmployeeNo());
        response.setTitle(row.title());
        response.setStatus(row.status());
        response.setStatusName(statusName(row.status()));
        response.setCurrentStepKey(row.currentStepKey());
        response.setCurrentStepName(row.currentStepName());
        response.setFlowableProcessInstanceId(row.flowableProcessInstanceId());
        response.setSubmittedAt(row.submittedAt());
        response.setFinishedAt(row.finishedAt());
        if (leave != null) {
            WorkflowLeaveDetailResponse leaveResponse = new WorkflowLeaveDetailResponse();
            leaveResponse.setLeaveTypeId(leave.leaveTypeId());
            leaveResponse.setLeaveName(leave.leaveName());
            leaveResponse.setStartAt(leave.startAt());
            leaveResponse.setEndAt(leave.endAt());
            leaveResponse.setDurationMinutes(leave.durationMinutes());
            leaveResponse.setReason(leave.reason());
            response.setLeave(leaveResponse);
        }
        if (clockCorrection != null) {
            WorkflowClockCorrectionDetailResponse clockCorrectionResponse =
                new WorkflowClockCorrectionDetailResponse();
            clockCorrectionResponse.setTargetDate(clockCorrection.targetDate());
            clockCorrectionResponse.setMissingPunchType(clockCorrection.missingPunchType());
            clockCorrectionResponse.setMissingPunchTypeName(punchTypeName(clockCorrection.missingPunchType()));
            clockCorrectionResponse.setExpectedPunchAt(clockCorrection.expectedPunchAt());
            clockCorrectionResponse.setReason(clockCorrection.reason());
            response.setClockCorrection(clockCorrectionResponse);
        }
        if (generic != null) {
            WorkflowGenericDetailResponse genericResponse = new WorkflowGenericDetailResponse();
            genericResponse.setProcessCode(generic.processCode());
            genericResponse.setSummary(generic.summary());
            genericResponse.setFormPayload(parseJsonPayload(generic.formPayloadJson()));
            response.setGeneric(genericResponse);
        }
        response.setAttachments(attachments.stream()
            .map(this::toAttachmentResponse)
            .toList());
        return response;
    }

    private WorkflowRequestAttachmentResponse toAttachmentResponse(WorkflowAttachmentRow row) {
        WorkflowRequestAttachmentResponse response = new WorkflowRequestAttachmentResponse();
        response.setAttachmentRefId(row.attachmentRefId());
        response.setDisplayName(row.displayName());
        response.setMimeType(row.mimeType());
        response.setFileSize(row.fileSize());
        response.setContentSha256(row.contentSha256());
        response.setScanStatus(row.scanStatus());
        response.setScanStatusName(scanStatusName(row.scanStatus()));
        return response;
    }

    private WorkflowTaskActionResponse toActionResponse(WorkflowTaskActionRow row) {
        WorkflowTaskActionResponse response = new WorkflowTaskActionResponse();
        response.setActionId(row.actionId());
        response.setRequestId(row.requestId());
        response.setTaskId(row.taskId());
        response.setStepKey(row.stepKey());
        response.setStepName(row.stepName());
        response.setActorType(row.actorType());
        response.setActorTypeName(actorTypeName(row.actorType()));
        response.setActorId(row.actorId());
        response.setActorName(row.actorName());
        response.setActorAccount(row.actorAccount());
        response.setActionType(row.actionType());
        response.setActionTypeName(actionName(row.actionType()));
        response.setComment(row.comment());
        response.setBeforeStatus(row.beforeStatus());
        response.setBeforeStatusName(statusName(row.beforeStatus()));
        response.setAfterStatus(row.afterStatus());
        response.setAfterStatusName(statusName(row.afterStatus()));
        response.setAgentDecisionJson(compactJson(row.agentDecisionJson()));
        response.setActedAt(row.actedAt());
        return response;
    }

    private String bizTypeName(String value) {
        if (LEAVE_BIZ_TYPE.equals(value)) return "请假";
        if (CLOCK_CORRECTION_BIZ_TYPE.equals(value)) return "补卡";
        if ("EXPENSE".equals(value)) return "费用报销";
        if ("BUSINESS_TRIP".equals(value)) return "出差";
        if ("OVERTIME".equals(value)) return "加班";
        if ("SEAL".equals(value)) return "用章";
        if ("PURCHASE".equals(value)) return "采购";
        if ("CERTIFICATE".equals(value)) return "证明开具";
        if ("BORROW".equals(value)) return "备用金借款";
        if ("PROJECT".equals(value)) return "项目立项";
        return "其他业务";
    }

    private String statusName(String value) {
        if (STATUS_PENDING_REVIEW.equals(value)) return "待预审";
        if (STATUS_IN_APPROVAL.equals(value)) return "审批中";
        if ("APPROVED".equals(value)) return "已通过";
        if (STATUS_REJECTED.equals(value)) return "已驳回";
        if ("WITHDRAWN".equals(value)) return "已撤回";
        return value == null ? null : "未知状态";
    }

    private String actionName(String value) {
        if ("APPROVE".equals(value)) return "通过";
        if ("REJECT".equals(value)) return "驳回";
        if ("WITHDRAW".equals(value)) return "撤回";
        if ("AGENT_PRE_REVIEW".equals(value)) return "AI 预审";
        return value == null ? null : "其他动作";
    }

    private String scanStatusName(String value) {
        if ("CLEAN".equals(value)) return "已通过";
        if ("FAILED".equals(value)) return "未通过";
        if ("PENDING".equals(value)) return "检测中";
        return value == null ? null : "未知状态";
    }

    private String actorTypeName(String value) {
        if ("USER".equals(value)) return "员工";
        if ("ADMIN".equals(value) || "ADMIN_USER".equals(value)) return "管理员";
        if ("AGENT".equals(value)) return "AI 助手";
        if ("SYSTEM".equals(value)) return "系统";
        return value == null ? null : "业务参与人";
    }

    private String priorityName(String value) {
        if ("HIGH".equals(value)) return "高";
        if ("LOW".equals(value)) return "低";
        if ("NORMAL".equals(value) || "MEDIUM".equals(value)) return "中";
        return value == null ? null : "普通";
    }

    private String punchTypeName(String value) {
        if (PUNCH_TYPE_ON_DUTY.equals(value)) return "上班卡";
        if (PUNCH_TYPE_OFF_DUTY.equals(value)) return "下班卡";
        return value == null ? null : "其他补卡类型";
    }

    private WorkflowRequestStatusResponse statusResponse(Long requestId, String status) {
        WorkflowRequestStatusResponse response = new WorkflowRequestStatusResponse();
        response.setRequestId(requestId);
        response.setStatus(status);
        response.setStatusName(statusName(status));
        return response;
    }

    private String buildRequestNo() {
        return buildRequestNo(LEAVE_BIZ_TYPE);
    }

    private String buildRequestNo(String bizType) {
        return "WF-" + bizType + "-" + java.time.LocalDateTime.now().format(REQUEST_NO_TIME_FORMATTER)
            + "-" + IdGenerator.nextIdString();
    }

    private String requestSummary(WorkflowLeaveDetailRow leaveDetail,
                                  WorkflowClockCorrectionDetailRow clockCorrection,
                                  WorkflowGenericDetailRow generic) {
        if (leaveDetail != null) {
            return leaveDetail.reason();
        }
        if (clockCorrection != null) {
            return clockCorrection.reason();
        }
        if (generic != null) {
            return generic.summary();
        }
        return null;
    }

    private String normalizeGenericProcessCode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!GENERIC_PROCESS_CODES.contains(normalized)) {
            throw new BizException(400, "暂不支持该OA流程");
        }
        return normalized;
    }

    private String buildGenericTitle(OrgUserDetailResponse applicant, String processCode, String fallbackTitle) {
        String processName = bizTypeName(processCode);
        if (StringUtils.hasText(fallbackTitle)) {
            processName = fallbackTitle.trim().replace("申请", "");
        }
        return applicant.getRealName() + "的" + processName + "申请";
    }

    private String compactJsonPayload(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BizException(400, "通用OA表单数据格式错误");
        }
    }

    private JsonNode parseJsonPayload(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String normalizePunchType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!PUNCH_TYPE_ON_DUTY.equals(normalized) && !PUNCH_TYPE_OFF_DUTY.equals(normalized)) {
            throw new BizException(400, "补卡类型仅支持 ON_DUTY/OFF_DUTY");
        }
        return normalized;
    }

    private String compactJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isObject()) {
                ObjectNode ordered = objectMapper.createObjectNode();
                putIfPresent(ordered, node, "decision");
                putIfPresent(ordered, node, "conclusion");
                putIfPresent(ordered, node, "reason");
                putIfPresent(ordered, node, "suggestion");
                putIfPresent(ordered, node, "riskLevel");
                putIfPresent(ordered, node, "reviewMode");
                putIfPresent(ordered, node, "checks");
                node.fields().forEachRemaining(entry -> {
                    if (!ordered.has(entry.getKey())) {
                        ordered.set(entry.getKey(), entry.getValue());
                    }
                });
                return objectMapper.writeValueAsString(ordered);
            }
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            return json;
        }
    }

    private void putIfPresent(ObjectNode target, JsonNode source, String fieldName) {
        JsonNode value = source.get(fieldName);
        if (value != null) {
            target.set(fieldName, value);
        }
    }
}
