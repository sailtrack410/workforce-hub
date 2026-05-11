package com.workforcehub.agent.controller;

import com.workforcehub.agent.dto.AdminAgentTaskAuditResponse;
import com.workforcehub.agent.dto.AdminAgentTaskFailureSummaryResponse;
import com.workforcehub.agent.dto.AdminAgentTaskMetricsResponse;
import com.workforcehub.agent.dto.AdminAgentTaskResponse;
import com.workforcehub.agent.dto.AdminAgentTaskToolCallResponse;
import com.workforcehub.agent.dto.AgentTaskListQuery;
import com.workforcehub.agent.dto.AgentTaskTimeoutScanRequest;
import com.workforcehub.agent.dto.AgentTaskTimeoutScanResponse;
import com.workforcehub.agent.scheduler.AgentTaskTimeoutJobService;
import com.workforcehub.agent.service.AgentService;
import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.response.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台 Agent 任务治理接口。
 */
@RestController
@RequestMapping("/api/admin/agent-tasks")
public class AdminAgentTaskController {

    private final AgentService agentService;
    private final AgentTaskTimeoutJobService timeoutJobService;

    public AdminAgentTaskController(AgentService agentService,
                                    AgentTaskTimeoutJobService timeoutJobService) {
        this.agentService = agentService;
        this.timeoutJobService = timeoutJobService;
    }

    @GetMapping
    public R<PageResult<AdminAgentTaskResponse>> listTasks(@Valid AgentTaskListQuery query) {
        return R.ok(agentService.pageAdminAgentTasks(query));
    }

    @GetMapping("/metrics")
    public R<AdminAgentTaskMetricsResponse> metrics() {
        return R.ok(agentService.getAdminAgentTaskMetrics());
    }

    @GetMapping("/failure-summary")
    public R<List<AdminAgentTaskFailureSummaryResponse>> failureSummary() {
        return R.ok(agentService.listAdminAgentTaskFailureSummary());
    }

    @GetMapping("/{taskId}")
    public R<AdminAgentTaskResponse> getTask(@PathVariable Long taskId) {
        return R.ok(agentService.getAdminAgentTask(taskId));
    }

    @GetMapping("/{taskId}/tool-calls")
    public R<List<AdminAgentTaskToolCallResponse>> listTaskToolCalls(@PathVariable Long taskId) {
        return R.ok(agentService.listAdminAgentTaskToolCalls(taskId));
    }

    @GetMapping("/{taskId}/audits")
    public R<List<AdminAgentTaskAuditResponse>> listTaskAudits(@PathVariable Long taskId) {
        return R.ok(agentService.listAdminAgentTaskAudits(taskId));
    }

    @PostMapping("/timeout-scan")
    public R<AgentTaskTimeoutScanResponse> triggerTimeoutScan(
            @Valid @RequestBody AgentTaskTimeoutScanRequest request) {
        return R.ok(AgentTaskTimeoutScanResponse.of(
            timeoutJobService.cancelExpiredWaitingTasks(request.safeBatchSize())
        ));
    }
}
