package com.workforcehub.agent.controller;

import com.workforcehub.agent.dto.AgentTaskTimeoutScanRequest;
import com.workforcehub.agent.dto.AgentTaskTimeoutScanResponse;
import com.workforcehub.agent.scheduler.AgentTaskTimeoutJobService;
import com.workforcehub.common.response.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 Agent 任务治理接口。
 */
@RestController
@RequestMapping("/api/admin/agent-tasks")
public class AdminAgentTaskController {

    private final AgentTaskTimeoutJobService timeoutJobService;

    public AdminAgentTaskController(AgentTaskTimeoutJobService timeoutJobService) {
        this.timeoutJobService = timeoutJobService;
    }

    @PostMapping("/timeout-scan")
    public R<AgentTaskTimeoutScanResponse> triggerTimeoutScan(
            @Valid @RequestBody AgentTaskTimeoutScanRequest request) {
        return R.ok(AgentTaskTimeoutScanResponse.of(
            timeoutJobService.cancelExpiredWaitingTasks(request.safeBatchSize())
        ));
    }
}
