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
import com.workforcehub.agent.dto.CreateLlmModelRequest;
import com.workforcehub.agent.dto.GrantAgentAclRequest;
import com.workforcehub.agent.dto.LlmModelResponse;
import com.workforcehub.agent.dto.ModelTestResponse;
import com.workforcehub.agent.dto.TrialRunRequest;
import com.workforcehub.agent.dto.TrialRunResponse;
import com.workforcehub.agent.dto.UpdateAgentVersionRequest;
import com.workforcehub.agent.dto.WorkflowRunRequest;
import com.workforcehub.agent.dto.WorkflowRunResponse;
import com.workforcehub.agent.dto.UpdateLlmModelRequest;
import com.workforcehub.agent.dto.WorkflowValidateRequest;
import com.workforcehub.agent.dto.WorkflowValidationResponse;
import com.workforcehub.agent.service.AgentService;
import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.response.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping
    public R<PageResult<AgentResponse>> listAgents(@RequestParam(defaultValue = "1") long pageNo,
                                                   @RequestParam(defaultValue = "20") long pageSize,
                                                   @RequestParam(required = false) String keyword) {
        return R.ok(agentService.pageAdminAgents(pageNo, pageSize, keyword));
    }

    @PostMapping
    public R<AgentResponse> createAgent(@Valid @RequestBody CreateAgentRequest request) {
        return R.ok(agentService.createAgent(request));
    }

    @GetMapping("/{agentId}")
    public R<AgentResponse> getAgent(@PathVariable Long agentId) {
        return R.ok(agentService.getAdminAgent(agentId));
    }

    @GetMapping("/{agentId}/versions")
    public R<List<AgentVersionResponse>> listVersions(@PathVariable Long agentId) {
        return R.ok(agentService.listAdminAgentVersions(agentId));
    }

    @GetMapping("/tools")
    public R<List<AgentToolResponse>> listTools() {
        return R.ok(agentService.listAdminAgentTools());
    }

    @GetMapping("/{agentId}/acl")
    public R<List<AgentAclResponse>> listAcl(@PathVariable Long agentId) {
        return R.ok(agentService.listAdminAgentAcl(agentId));
    }

    @PostMapping("/workflows/validate")
    public R<WorkflowValidationResponse> validateWorkflow(@Valid @RequestBody WorkflowValidateRequest request) {
        return R.ok(agentService.validateWorkflow(request.getWorkflowConfigJson()));
    }

    @PostMapping("/{agentId}/versions")
    public R<AgentVersionResponse> createVersion(@PathVariable Long agentId,
                                                 @Valid @RequestBody CreateAgentVersionRequest request) {
        return R.ok(agentService.createVersion(agentId, request));
    }

    @PutMapping("/versions/{versionId}")
    public R<AgentVersionResponse> updateVersion(@PathVariable Long versionId,
                                                 @Valid @RequestBody UpdateAgentVersionRequest request) {
        return R.ok(agentService.updateVersion(versionId, request));
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

    @PostMapping("/{agentId}/disable")
    public R<AgentResponse> disableAgent(@PathVariable Long agentId) {
        return R.ok(agentService.disableAgent(agentId));
    }

    @PostMapping("/{agentId}/enable")
    public R<AgentResponse> enableAgent(@PathVariable Long agentId) {
        return R.ok(agentService.enableAgent(agentId));
    }

    // ==================== LLM Models ====================

    @GetMapping("/llm-models")
    public R<List<LlmModelResponse>> listLlmModels() {
        return R.ok(agentService.listLlmModels());
    }

    @PostMapping("/llm-models")
    public R<LlmModelResponse> createLlmModel(@Valid @RequestBody CreateLlmModelRequest request) {
        return R.ok(agentService.createLlmModel(request));
    }

    @PutMapping("/llm-models/{modelId}")
    public R<LlmModelResponse> updateLlmModel(@PathVariable Long modelId,
                                               @Valid @RequestBody UpdateLlmModelRequest request) {
        return R.ok(agentService.updateLlmModel(modelId, request));
    }

    @DeleteMapping("/llm-models/{modelId}")
    public R<Void> deleteLlmModel(@PathVariable Long modelId) {
        agentService.deleteLlmModel(modelId);
        return R.ok();
    }

    @PostMapping("/llm-models/{modelId}/test")
    public R<ModelTestResponse> testModel(@PathVariable Long modelId) {
        return R.ok(agentService.testModelConnection(modelId));
    }

    // ==================== Trial Run ====================

    @PostMapping("/trial-run")
    public R<TrialRunResponse> trialRun(@Valid @RequestBody TrialRunRequest request) {
        return R.ok(agentService.trialRun(request));
    }

    @PostMapping("/workflow-run")
    public R<WorkflowRunResponse> runWorkflow(@Valid @RequestBody WorkflowRunRequest request) {
        return R.ok(agentService.runWorkflow(request));
    }
}
