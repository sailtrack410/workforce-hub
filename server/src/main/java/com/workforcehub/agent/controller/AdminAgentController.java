package com.workforcehub.agent.controller;

import com.workforcehub.agent.dto.AgentAclResponse;
import com.workforcehub.agent.dto.AgentResponse;
import com.workforcehub.agent.dto.AgentToolBindingResponse;
import com.workforcehub.agent.dto.AgentToolResponse;
import com.workforcehub.agent.dto.AgentVersionResponse;
import com.workforcehub.agent.dto.BindAgentToolRequest;
import com.workforcehub.agent.dto.CreateAgentRequest;
import com.workforcehub.agent.dto.CreateAgentToolRequest;
import com.workforcehub.agent.dto.CreateAgentVersionRequest;
import com.workforcehub.agent.dto.GrantAgentAclRequest;
import com.workforcehub.agent.service.AgentService;
import com.workforcehub.common.response.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 Agent 治理接口。
 */
@RestController
@RequestMapping("/api/admin/agents")
public class AdminAgentController {

    private final AgentService agentService;

    public AdminAgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping
    public R<AgentResponse> createAgent(@Valid @RequestBody CreateAgentRequest request) {
        return R.ok(agentService.createAgent(request));
    }

    @PostMapping("/{agentId}/versions")
    public R<AgentVersionResponse> createVersion(@PathVariable Long agentId,
                                                 @Valid @RequestBody CreateAgentVersionRequest request) {
        return R.ok(agentService.createVersion(agentId, request));
    }

    @PostMapping("/tools")
    public R<AgentToolResponse> createTool(@Valid @RequestBody CreateAgentToolRequest request) {
        return R.ok(agentService.createTool(request));
    }

    @PostMapping("/versions/{versionId}/tools")
    public R<AgentToolBindingResponse> bindTool(@PathVariable Long versionId,
                                                @Valid @RequestBody BindAgentToolRequest request) {
        return R.ok(agentService.bindTool(versionId, request));
    }

    @PostMapping("/{agentId}/acl")
    public R<AgentAclResponse> grantAcl(@PathVariable Long agentId,
                                        @Valid @RequestBody GrantAgentAclRequest request) {
        return R.ok(agentService.grantAcl(agentId, request));
    }

    @PostMapping("/versions/{versionId}/publish")
    public R<AgentResponse> publishVersion(@PathVariable Long versionId) {
        return R.ok(agentService.publishVersion(versionId));
    }
}
