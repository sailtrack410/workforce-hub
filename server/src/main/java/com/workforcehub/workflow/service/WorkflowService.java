package com.workforcehub.workflow.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.org.dto.OrgUserDetailResponse;
import com.workforcehub.org.service.OrgDirectoryService;
import com.workforcehub.workflow.dto.LeaveRequestCreateRequest;
import com.workforcehub.workflow.dto.LeaveRequestCreateResponse;
import com.workforcehub.workflow.dto.WorkflowLeaveDetailResponse;
import com.workforcehub.workflow.dto.WorkflowRequestAttachmentResponse;
import com.workforcehub.workflow.dto.WorkflowRequestDetailResponse;
import com.workforcehub.workflow.dto.WorkflowRequestListQuery;
import com.workforcehub.workflow.dto.WorkflowRequestStatusResponse;
import com.workforcehub.workflow.dto.WorkflowRequestSummaryResponse;
import com.workforcehub.workflow.dto.WorkflowRequestWithdrawRequest;
import com.workforcehub.workflow.dto.WorkflowTaskApproveRequest;
import com.workforcehub.workflow.dto.WorkflowTaskActionResponse;
import com.workforcehub.workflow.dto.WorkflowTaskRejectRequest;
import com.workforcehub.workflow.dto.WorkflowTodoResponse;
import com.workforcehub.workflow.repository.WorkflowAttachmentRow;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * module-workflow 第一版审批服务。
 */
@Service
@ConditionalOnProperty(prefix = "workforce.workflow", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowService {

    private static final String LEAVE_PROCESS_CODE = "LEAVE";
    private static final DateTimeFormatter REQUEST_NO_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final WorkflowRepository workflowRepository;
    private final OrgDirectoryService orgDirectoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;

    public WorkflowService(WorkflowRepository workflowRepository,
                           OrgDirectoryService orgDirectoryService,
                           RuntimeService runtimeService,
                           TaskService taskService) {
        this.workflowRepository = workflowRepository;
        this.orgDirectoryService = orgDirectoryService;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
    }

    @Transactional(rollbackFor = Exception.class)
    public LeaveRequestCreateResponse submitLeaveRequest(LeaveRequestCreateRequest request) {
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
        Long requestId = IdWorker.getId();
        Long leaveDetailId = IdWorker.getId();
        workflowRepository.insertWorkflowRequest(
            requestId,
            requestNo,
            leaveDetailId,
            applicantUserId,
            applicant.getPrimaryDeptId(),
            applicant.getPositionId(),
            title,
            templateVersion.id()
        );
        workflowRepository.insertLeaveDetail(leaveDetailId, requestId, request);
        bindAttachments(requestId, applicantUserId, request.getAttachmentRefIds());

        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", requestId);
        variables.put("applicantUserId", String.valueOf(applicantUserId));
        variables.put("managerUserId", String.valueOf(applicant.getManagerUserId()));
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
            templateVersion.flowableDefinitionKey(),
            String.valueOf(requestId),
            variables
        );
        workflowRepository.updateProcessInstanceId(requestId, processInstance.getProcessInstanceId());

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
            request.getReason()
        );

        LeaveRequestCreateResponse response = new LeaveRequestCreateResponse();
        response.setRequestId(requestId);
        response.setRequestNo(requestNo);
        response.setStatus("IN_APPROVAL");
        response.setFlowableProcessInstanceId(processInstance.getProcessInstanceId());
        return response;
    }

    public List<WorkflowTodoResponse> listCurrentEmployeeTodos() {
        Long ownerUserId = CurrentUserHolder.requireUserId();
        return workflowRepository.listPendingTodos(ownerUserId).stream()
            .map(this::toTodoResponse)
            .toList();
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
        if (!"IN_APPROVAL".equals(workflowRequest.status())) {
            throw new BizException(400, "当前申请状态不允许撤回");
        }

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
        List<WorkflowAttachmentRow> attachments = workflowRepository.listAttachmentsByRequestId(requestId);
        return toRequestDetailResponse(row, leave, attachments);
    }

    public List<WorkflowTaskActionResponse> listRequestActions(Long requestId) {
        Long userId = CurrentUserHolder.requireUserId();
        ensureRequestParticipant(requestId, userId);
        return workflowRepository.listTaskActions(requestId).stream()
            .map(this::toActionResponse)
            .toList();
    }

    private WorkflowRequestStatusResponse completeManagerTask(String taskId,
                                                              String comment,
                                                              String actionType,
                                                              String afterStatus) {
        Long approverUserId = CurrentUserHolder.requireUserId();
        WorkflowRequestRow workflowRequest = workflowRepository.getRequestByTaskIdOrThrow(taskId);
        Task task = taskService.createTaskQuery()
            .taskId(taskId)
            .taskAssignee(String.valueOf(approverUserId))
            .singleResult();
        if (task == null) {
            throw new BizException(403, "当前用户不是该审批任务处理人");
        }
        if (!"IN_APPROVAL".equals(workflowRequest.status())) {
            throw new BizException(400, "当前申请状态不允许审批");
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
        return statusResponse(workflowRequest.id(), afterStatus);
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

    private void validateLeaveRequest(LeaveRequestCreateRequest request) {
        if (!request.getEndAt().isAfter(request.getStartAt())) {
            throw new BizException(400, "结束时间必须晚于开始时间");
        }
        if (!workflowRepository.existsActiveLeaveType(request.getLeaveTypeId())) {
            throw new BizException(404, "假种不存在或已停用");
        }
    }

    private WorkflowTodoResponse toTodoResponse(WorkflowTodoRow row) {
        WorkflowTodoResponse response = new WorkflowTodoResponse();
        response.setTodoId(row.todoId());
        response.setRequestId(row.requestId());
        response.setTaskId(row.taskId());
        response.setTitle(row.title());
        response.setSummary(row.summary());
        response.setStatus(row.status());
        response.setPriority(row.priority());
        response.setCreatedAt(row.createdAt());
        return response;
    }

    private WorkflowRequestSummaryResponse toRequestSummaryResponse(WorkflowRequestSummaryRow row) {
        WorkflowRequestSummaryResponse response = new WorkflowRequestSummaryResponse();
        response.setRequestId(row.id());
        response.setRequestNo(row.requestNo());
        response.setBizType(row.bizType());
        response.setBizId(row.bizId());
        response.setTitle(row.title());
        response.setStatus(row.status());
        response.setCurrentStepKey(row.currentStepKey());
        response.setCurrentStepName(row.currentStepName());
        response.setSubmittedAt(row.submittedAt());
        response.setFinishedAt(row.finishedAt());
        return response;
    }

    private WorkflowRequestDetailResponse toRequestDetailResponse(WorkflowRequestDetailRow row,
                                                                  WorkflowLeaveDetailRow leave,
                                                                  List<WorkflowAttachmentRow> attachments) {
        WorkflowRequestDetailResponse response = new WorkflowRequestDetailResponse();
        response.setRequestId(row.id());
        response.setRequestNo(row.requestNo());
        response.setBizType(row.bizType());
        response.setBizId(row.bizId());
        response.setApplicantUserId(row.applicantUserId());
        response.setTitle(row.title());
        response.setStatus(row.status());
        response.setCurrentStepKey(row.currentStepKey());
        response.setCurrentStepName(row.currentStepName());
        response.setFlowableProcessInstanceId(row.flowableProcessInstanceId());
        response.setSubmittedAt(row.submittedAt());
        response.setFinishedAt(row.finishedAt());
        if (leave != null) {
            WorkflowLeaveDetailResponse leaveResponse = new WorkflowLeaveDetailResponse();
            leaveResponse.setLeaveTypeId(leave.leaveTypeId());
            leaveResponse.setStartAt(leave.startAt());
            leaveResponse.setEndAt(leave.endAt());
            leaveResponse.setDurationMinutes(leave.durationMinutes());
            leaveResponse.setReason(leave.reason());
            response.setLeave(leaveResponse);
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
        response.setActorId(row.actorId());
        response.setActionType(row.actionType());
        response.setComment(row.comment());
        response.setBeforeStatus(row.beforeStatus());
        response.setAfterStatus(row.afterStatus());
        response.setActedAt(row.actedAt());
        return response;
    }

    private WorkflowRequestStatusResponse statusResponse(Long requestId, String status) {
        WorkflowRequestStatusResponse response = new WorkflowRequestStatusResponse();
        response.setRequestId(requestId);
        response.setStatus(status);
        return response;
    }

    private String buildRequestNo() {
        return "WF-LEAVE-" + java.time.LocalDateTime.now().format(REQUEST_NO_TIME_FORMATTER)
            + "-" + IdWorker.getIdStr();
    }
}
