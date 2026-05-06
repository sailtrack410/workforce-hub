package com.workforcehub.workflow.controller;

import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.response.R;
import com.workforcehub.workflow.dto.LeaveRequestCreateRequest;
import com.workforcehub.workflow.dto.LeaveRequestCreateResponse;
import com.workforcehub.workflow.dto.WorkflowRequestDetailResponse;
import com.workforcehub.workflow.dto.WorkflowRequestListQuery;
import com.workforcehub.workflow.dto.WorkflowRequestStatusResponse;
import com.workforcehub.workflow.dto.WorkflowRequestSummaryResponse;
import com.workforcehub.workflow.dto.WorkflowRequestWithdrawRequest;
import com.workforcehub.workflow.dto.WorkflowTaskApproveRequest;
import com.workforcehub.workflow.dto.WorkflowTaskActionResponse;
import com.workforcehub.workflow.dto.WorkflowTaskRejectRequest;
import com.workforcehub.workflow.dto.WorkflowTodoResponse;
import com.workforcehub.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工端审批流程接口。
 */
@RestController
@ConditionalOnProperty(prefix = "workforce.workflow", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/employee/workflow")
public class EmployeeWorkflowController {

    private final WorkflowService workflowService;

    public EmployeeWorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping("/leave-requests")
    public R<LeaveRequestCreateResponse> submitLeaveRequest(
            @Valid @RequestBody LeaveRequestCreateRequest request) {
        return R.ok(workflowService.submitLeaveRequest(request));
    }

    @GetMapping("/todos")
    public R<List<WorkflowTodoResponse>> todos() {
        return R.ok(workflowService.listCurrentEmployeeTodos());
    }

    @GetMapping("/requests")
    public R<PageResult<WorkflowRequestSummaryResponse>> requests(@Valid WorkflowRequestListQuery query) {
        return R.ok(workflowService.pageCurrentEmployeeRequests(query));
    }

    @PostMapping("/tasks/{taskId}/approve")
    public R<WorkflowRequestStatusResponse> approveTask(@PathVariable String taskId,
                                                      @Valid @RequestBody WorkflowTaskApproveRequest request) {
        return R.ok(workflowService.approveTask(taskId, request));
    }

    @PostMapping("/tasks/{taskId}/reject")
    public R<WorkflowRequestStatusResponse> rejectTask(@PathVariable String taskId,
                                                       @Valid @RequestBody WorkflowTaskRejectRequest request) {
        return R.ok(workflowService.rejectTask(taskId, request));
    }

    @PostMapping("/requests/{requestId}/withdraw")
    public R<WorkflowRequestStatusResponse> withdrawRequest(@PathVariable Long requestId,
                                                            @Valid @RequestBody WorkflowRequestWithdrawRequest request) {
        return R.ok(workflowService.withdrawRequest(requestId, request));
    }

    @GetMapping("/requests/{requestId}")
    public R<WorkflowRequestDetailResponse> requestDetail(@PathVariable Long requestId) {
        return R.ok(workflowService.getRequestDetail(requestId));
    }

    @GetMapping("/requests/{requestId}/actions")
    public R<List<WorkflowTaskActionResponse>> requestActions(@PathVariable Long requestId) {
        return R.ok(workflowService.listRequestActions(requestId));
    }
}
