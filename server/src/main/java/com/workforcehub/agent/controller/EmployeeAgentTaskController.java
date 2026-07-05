package com.workforcehub.agent.controller;

import com.workforcehub.agent.dto.AgentTaskResponse;
import com.workforcehub.agent.dto.CancelAgentTaskRequest;
import com.workforcehub.agent.dto.ConfirmAgentTaskRequest;
import com.workforcehub.agent.service.AgentService;
import com.workforcehub.common.response.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工端 Agent 任务动作接口。
 */
// Agent 任务动作：确认/拒绝、取消、反馈
@RestController
@RequestMapping("/api/employee/agent-tasks")
public class EmployeeAgentTaskController {

    private final AgentService agentService;

    public EmployeeAgentTaskController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/{taskId}/confirm")
    public R<AgentTaskResponse> confirmTask(@PathVariable Long taskId,
                                            @Valid @RequestBody ConfirmAgentTaskRequest request) {
        return R.ok(agentService.confirmCurrentEmployeeTask(taskId, request));
    }

    @PostMapping("/{taskId}/cancel")
    public R<AgentTaskResponse> cancelTask(@PathVariable Long taskId,
                                           @Valid @RequestBody CancelAgentTaskRequest request) {
        return R.ok(agentService.cancelCurrentEmployeeTask(taskId, request));
    }
}
