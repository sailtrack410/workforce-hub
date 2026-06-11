package com.workforcehub.agent.controller;

import com.workforcehub.agent.dto.AgentResponse;
import com.workforcehub.agent.dto.AgentTaskResponse;
import com.workforcehub.agent.dto.CreateAgentTaskRequest;
import com.workforcehub.agent.service.AgentService;
import com.workforcehub.common.response.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工端 Agent 使用接口。
 */
@RestController
@RequestMapping("/api/employee/agents")
public class EmployeeAgentController {

    private final AgentService agentService;

    public EmployeeAgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping
    public R<List<AgentResponse>> listAgents(@RequestParam(required = false) String keyword) {
        return R.ok(agentService.listCurrentEmployeeAgents(keyword));
    }

    @PostMapping("/{agentId}/tasks")
    public R<AgentTaskResponse> createTask(@PathVariable Long agentId,
                                           @Valid @RequestBody CreateAgentTaskRequest request) {
        return R.ok(agentService.createCurrentEmployeeTask(agentId, request));
    }
}
