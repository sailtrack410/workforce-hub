package com.workforcehub.workflow.controller;

import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.response.R;
import com.workforcehub.file.controller.FileDownloadResponseFactory;
import com.workforcehub.workflow.dto.ClockCorrectionRequestCreateRequest;
import com.workforcehub.file.dto.CreateDownloadTicketRequest;
import com.workforcehub.file.dto.DownloadFileResult;
import com.workforcehub.file.dto.DownloadTicketResponse;
import com.workforcehub.workflow.dto.GenericWorkflowRequestCreateRequest;
import com.workforcehub.workflow.dto.LeaveRequestCreateRequest;
import com.workforcehub.workflow.dto.WorkflowRequestCreateResponse;
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
import com.workforcehub.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工端审批流程接口。
 */
// 员工审批接口：提交请假/补卡、待办列表、审批通过/驳回、撤回、请求详情
@RestController
@ConditionalOnProperty(prefix = "workforce.workflow", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/employee/workflow")
public class EmployeeWorkflowController {

    private final WorkflowService workflowService;
    private final FileDownloadResponseFactory downloadResponseFactory;

    public EmployeeWorkflowController(WorkflowService workflowService,
                                      FileDownloadResponseFactory downloadResponseFactory) {
        this.workflowService = workflowService;
        this.downloadResponseFactory = downloadResponseFactory;
    }

    @Operation(summary = "提交请假申请", operationId = "submitLeaveRequest")
    @PostMapping("/leave-requests")
    public R<WorkflowRequestCreateResponse> submitLeaveRequest(
            @Valid @RequestBody LeaveRequestCreateRequest request) {
        return R.ok(workflowService.submitLeaveRequest(request));
    }

    @Operation(summary = "提交补卡申请", operationId = "submitClockCorrectionRequest")
    @PostMapping("/clock-correction-requests")
    public R<WorkflowRequestCreateResponse> submitClockCorrectionRequest(
            @Valid @RequestBody ClockCorrectionRequestCreateRequest request) {
        return R.ok(workflowService.submitClockCorrectionRequest(request));
    }

    @Operation(summary = "提交通用审批申请", operationId = "submitGenericRequest")
    @PostMapping("/generic-requests")
    public R<WorkflowRequestCreateResponse> submitGenericRequest(
            @Valid @RequestBody GenericWorkflowRequestCreateRequest request) {
        return R.ok(workflowService.submitGenericRequest(request));
    }

    @Operation(summary = "查询我发起的审批统计", operationId = "getMyWorkflowRequestSummary")
    @GetMapping("/requests/summary")
    public R<WorkflowRequestStatResponse> myRequestSummary() {
        return R.ok(workflowService.myRequestSummary());
    }

    @Operation(summary = "分页查询抄送我的审批", operationId = "pageMyWorkflowCcList")
    @GetMapping("/cc-list")
    public R<PageResult<WorkflowCcResponse>> pageCcToMe(
        @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
        @RequestParam(name = "pageSize", defaultValue = "20") long pageSize
    ) {
        return R.ok(workflowService.pageCcToMe(pageNo, pageSize));
    }

    @Operation(summary = "标记抄送已读", operationId = "markMyWorkflowCcRead")
    @PostMapping("/cc-list/{requestId}/read")
    public R<Void> markCcRead(@PathVariable String requestId) {
        workflowService.markCcRead(requestId);
        return R.ok(null);
    }

    @Operation(summary = "查询本周AI预审统计", operationId = "getMyAiReviewSummary")
    @GetMapping("/ai-review/summary")
    public R<WorkflowAiReviewSummaryResponse> aiReviewSummary() {
        return R.ok(workflowService.aiReviewSummary());
    }

    @Operation(summary = "查询我的待办任务", operationId = "listMyWorkflowTodos")
    @GetMapping("/todos")
    public R<List<WorkflowTodoResponse>> todos() {
        return R.ok(workflowService.listCurrentEmployeeTodos());
    }

    @Operation(summary = "分页查询我的审批请求", operationId = "pageMyWorkflowRequests")
    @GetMapping("/requests")
    public R<PageResult<WorkflowRequestSummaryResponse>> requests(@Valid WorkflowRequestListQuery query) {
        return R.ok(workflowService.pageCurrentEmployeeRequests(query));
    }

    @Operation(summary = "审批通过任务", operationId = "approveWorkflowTask")
    @PostMapping("/tasks/{taskId}/approve")
    public R<WorkflowRequestStatusResponse> approveTask(@PathVariable String taskId,
                                                      @Valid @RequestBody WorkflowTaskApproveRequest request) {
        return R.ok(workflowService.approveTask(taskId, request));
    }

    @Operation(summary = "驳回审批任务", operationId = "rejectWorkflowTask")
    @PostMapping("/tasks/{taskId}/reject")
    public R<WorkflowRequestStatusResponse> rejectTask(@PathVariable String taskId,
                                                       @Valid @RequestBody WorkflowTaskRejectRequest request) {
        return R.ok(workflowService.rejectTask(taskId, request));
    }

    @Operation(summary = "撤回审批请求", operationId = "withdrawWorkflowRequest")
    @PostMapping("/requests/{requestId}/withdraw")
    public R<WorkflowRequestStatusResponse> withdrawRequest(@PathVariable Long requestId,
                                                            @Valid @RequestBody WorkflowRequestWithdrawRequest request) {
        return R.ok(workflowService.withdrawRequest(requestId, request));
    }

    @Operation(summary = "查询我的审批请求详情", operationId = "getMyWorkflowRequest")
    @GetMapping("/requests/{requestId}")
    public R<WorkflowRequestDetailResponse> requestDetail(@PathVariable Long requestId) {
        return R.ok(workflowService.getRequestDetail(requestId));
    }

    @Operation(summary = "查询请求可用操作", operationId = "listMyWorkflowRequestActions")
    @GetMapping("/requests/{requestId}/actions")
    public R<List<WorkflowTaskActionResponse>> requestActions(@PathVariable Long requestId) {
        return R.ok(workflowService.listRequestActions(requestId));
    }

    @Operation(summary = "创建附件下载票据", operationId = "createWorkflowAttachmentDownloadTicket")
    @PostMapping("/requests/{requestId}/attachments/{attachmentRefId}/download-tickets")
    public R<DownloadTicketResponse> createAttachmentDownloadTicket(@PathVariable Long requestId,
                                                                    @PathVariable Long attachmentRefId,
                                                                    @Valid @RequestBody CreateDownloadTicketRequest request) {
        return R.ok(workflowService.createAttachmentDownloadTicket(requestId, attachmentRefId, request));
    }

    @Operation(summary = "下载审批附件", operationId = "downloadWorkflowAttachment")
    @GetMapping("/attachments/download/{ticketNo}")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable String ticketNo) {
        DownloadFileResult result = workflowService.downloadAttachment(ticketNo);
        return downloadResponseFactory.attachment(result);
    }
}
