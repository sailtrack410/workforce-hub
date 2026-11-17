package com.workforcehub.agent.controller;

import com.workforcehub.agent.dto.AdminAgentTaskAuditResponse;
import com.workforcehub.agent.dto.AdminAgentTaskFailureSummaryResponse;
import com.workforcehub.agent.dto.AdminAgentTaskMetricsResponse;
import com.workforcehub.agent.dto.AdminAgentTaskResponse;
import com.workforcehub.agent.dto.AdminAgentTaskTimeoutRunResponse;
import com.workforcehub.agent.dto.AdminAgentTaskToolCallResponse;
import com.workforcehub.agent.dto.AgentTaskListQuery;
import com.workforcehub.agent.dto.AgentTaskTimeoutRunQuery;
import com.workforcehub.agent.dto.AgentTaskTimeoutScanRequest;
import com.workforcehub.agent.dto.AgentTaskTimeoutScanResponse;
import com.workforcehub.agent.scheduler.AgentTaskTimeoutJobService;
import com.workforcehub.agent.service.AgentService;
import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
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
// Agent 任务后台接口：任务列表、详情、工具调用记录、指标统计、超时扫描
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

    @Operation(summary = "分页查询 Agent 任务", operationId = "pageAgentTasks")
    @GetMapping
    public R<PageResult<AdminAgentTaskResponse>> listTasks(@Valid AgentTaskListQuery query) {
        return R.ok(agentService.pageAdminAgentTasks(query));
    }

    @Operation(summary = "查询 Agent 任务指标", operationId = "getAgentTaskMetrics")
    @GetMapping("/metrics")
    public R<AdminAgentTaskMetricsResponse> metrics() {
        return R.ok(agentService.getAdminAgentTaskMetrics());
    }

    @Operation(summary = "查询任务失败汇总", operationId = "listAgentTaskFailureSummary")
    @GetMapping("/failure-summary")
    public R<List<AdminAgentTaskFailureSummaryResponse>> failureSummary() {
        return R.ok(agentService.listAdminAgentTaskFailureSummary());
    }

    @Operation(summary = "分页查询超时扫描记录", operationId = "pageAgentTaskTimeoutScanRuns")
    @GetMapping("/timeout-scan-runs")
    public R<PageResult<AdminAgentTaskTimeoutRunResponse>> listTimeoutScanRuns(
            @Valid AgentTaskTimeoutRunQuery query) {
        return R.ok(agentService.pageAdminAgentTaskTimeoutRuns(query));
    }

    @Operation(summary = "查询 Agent 任务详情", operationId = "getAgentTask")
    @GetMapping("/{taskId}")
    public R<AdminAgentTaskResponse> getTask(@PathVariable Long taskId) {
        return R.ok(agentService.getAdminAgentTask(taskId));
    }

    @Operation(summary = "查询任务工具调用记录", operationId = "listAgentTaskToolCalls")
    @GetMapping("/{taskId}/tool-calls")
    public R<List<AdminAgentTaskToolCallResponse>> listTaskToolCalls(@PathVariable Long taskId) {
        return R.ok(agentService.listAdminAgentTaskToolCalls(taskId));
    }

    @Operation(summary = "查询任务审计记录", operationId = "listAgentTaskAudits")
    @GetMapping("/{taskId}/audits")
    public R<List<AdminAgentTaskAuditResponse>> listTaskAudits(@PathVariable Long taskId) {
        return R.ok(agentService.listAdminAgentTaskAudits(taskId));
    }

    @Operation(summary = "手动触发任务超时扫描", operationId = "triggerAgentTaskTimeoutScan")
    @PostMapping("/timeout-scan")
    public R<AgentTaskTimeoutScanResponse> triggerTimeoutScan(
            @Valid @RequestBody AgentTaskTimeoutScanRequest request) {
        return R.ok(timeoutJobService.runTimeoutScan("MANUAL", request.safeBatchSize()));
    }
}
