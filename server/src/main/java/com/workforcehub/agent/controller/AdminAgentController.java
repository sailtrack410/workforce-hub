package com.workforcehub.agent.controller;

import com.workforcehub.agent.dto.AgentAclResponse;
import com.workforcehub.agent.dto.AgentResponse;
import com.workforcehub.agent.dto.AgentToolBindingResponse;
import com.workforcehub.agent.dto.AgentToolResponse;
import com.workforcehub.agent.dto.AgentVersionResponse;
import com.workforcehub.agent.dto.AiProviderResponse;
import com.workforcehub.agent.dto.BindAgentToolRequest;
import com.workforcehub.agent.dto.CreateAgentRequest;
import com.workforcehub.agent.dto.CreateAgentToolRequest;
import com.workforcehub.agent.dto.CreateAgentVersionRequest;
import com.workforcehub.agent.dto.CreateLlmModelRequest;
import com.workforcehub.agent.dto.GrantAgentAclRequest;
import com.workforcehub.agent.dto.LlmModelResponse;
import com.workforcehub.agent.dto.ModelTestResponse;
import com.workforcehub.agent.dto.ProviderModelOptionResponse;
import com.workforcehub.agent.dto.SaveAiProviderRequest;
import com.workforcehub.agent.dto.TestLlmModelRequest;
import com.workforcehub.agent.dto.TrialRunRequest;
import com.workforcehub.agent.dto.TrialRunResponse;
import com.workforcehub.agent.dto.UpdateAgentVersionRequest;
import com.workforcehub.agent.dto.UpdateAgentRequest;
import com.workforcehub.agent.dto.WorkflowRunRequest;
import com.workforcehub.agent.dto.WorkflowRunResponse;
import com.workforcehub.agent.dto.UpdateLlmModelRequest;
import com.workforcehub.agent.dto.WorkflowValidateRequest;
import com.workforcehub.agent.dto.WorkflowValidationResponse;
import com.workforcehub.agent.service.AgentService;
import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
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
// Agent 后台接口：Agent/版本/工具/ACL CRUD，LLM 模型管理，任务列表，试运行
@RestController
@RequestMapping("/api/admin/agents")
public class AdminAgentController {

    private final AgentService agentService;

    public AdminAgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @Operation(summary = "分页查询 Agent 列表", operationId = "pageAgents")
    @GetMapping
    public R<PageResult<AgentResponse>> listAgents(@RequestParam(defaultValue = "1") long pageNo,
                                                   @RequestParam(defaultValue = "20") long pageSize,
                                                   @RequestParam(required = false) String keyword) {
        return R.ok(agentService.pageAdminAgents(pageNo, pageSize, keyword));
    }

    @Operation(summary = "创建 Agent", operationId = "createAgent")
    @PostMapping
    public R<AgentResponse> createAgent(@Valid @RequestBody CreateAgentRequest request) {
        return R.ok(agentService.createAgent(request));
    }

    @Operation(summary = "查询 Agent 详情", operationId = "getAgent")
    @GetMapping("/{agentId}")
    public R<AgentResponse> getAgent(@PathVariable Long agentId) {
        return R.ok(agentService.getAdminAgent(agentId));
    }

    @Operation(summary = "更新 Agent", operationId = "updateAgent")
    @PutMapping("/{agentId}")
    public R<AgentResponse> updateAgent(@PathVariable Long agentId,
                                        @Valid @RequestBody UpdateAgentRequest request) {
        return R.ok(agentService.updateAgent(agentId, request));
    }

    @Operation(summary = "删除 Agent", operationId = "deleteAgent")
    @DeleteMapping("/{agentId}")
    public R<Void> deleteAgent(@PathVariable Long agentId) {
        agentService.deleteAgent(agentId);
        return R.ok();
    }

    @Operation(summary = "查询 Agent 版本列表", operationId = "listAgentVersions")
    @GetMapping("/{agentId}/versions")
    public R<List<AgentVersionResponse>> listVersions(@PathVariable Long agentId) {
        return R.ok(agentService.listAdminAgentVersions(agentId));
    }

    @Operation(summary = "查询 Agent 工具列表", operationId = "listAgentTools")
    @GetMapping("/tools")
    public R<List<AgentToolResponse>> listTools() {
        return R.ok(agentService.listAdminAgentTools());
    }

    @Operation(summary = "查询 Agent ACL 列表", operationId = "listAgentAcl")
    @GetMapping("/{agentId}/acl")
    public R<List<AgentAclResponse>> listAcl(@PathVariable Long agentId) {
        return R.ok(agentService.listAdminAgentAcl(agentId));
    }

    @Operation(summary = "校验 Agent 工作流配置", operationId = "validateAgentWorkflow")
    @PostMapping("/workflows/validate")
    public R<WorkflowValidationResponse> validateWorkflow(@Valid @RequestBody WorkflowValidateRequest request) {
        return R.ok(agentService.validateWorkflow(request.getWorkflowConfigJson()));
    }

    @Operation(summary = "创建 Agent 版本", operationId = "createAgentVersion")
    @PostMapping("/{agentId}/versions")
    public R<AgentVersionResponse> createVersion(@PathVariable Long agentId,
                                                 @Valid @RequestBody CreateAgentVersionRequest request) {
        return R.ok(agentService.createVersion(agentId, request));
    }

    @Operation(summary = "更新 Agent 版本", operationId = "updateAgentVersion")
    @PutMapping("/versions/{versionId}")
    public R<AgentVersionResponse> updateVersion(@PathVariable Long versionId,
                                                 @Valid @RequestBody UpdateAgentVersionRequest request) {
        return R.ok(agentService.updateVersion(versionId, request));
    }

    @Operation(summary = "创建 Agent 工具", operationId = "createAgentTool")
    @PostMapping("/tools")
    public R<AgentToolResponse> createTool(@Valid @RequestBody CreateAgentToolRequest request) {
        return R.ok(agentService.createTool(request));
    }

    @Operation(summary = "绑定工具到版本", operationId = "bindAgentTool")
    @PostMapping("/versions/{versionId}/tools")
    public R<AgentToolBindingResponse> bindTool(@PathVariable Long versionId,
                                                @Valid @RequestBody BindAgentToolRequest request) {
        return R.ok(agentService.bindTool(versionId, request));
    }

    @Operation(summary = "授予 Agent ACL 权限", operationId = "grantAgentAcl")
    @PostMapping("/{agentId}/acl")
    public R<AgentAclResponse> grantAcl(@PathVariable Long agentId,
                                        @Valid @RequestBody GrantAgentAclRequest request) {
        return R.ok(agentService.grantAcl(agentId, request));
    }

    @Operation(summary = "发布 Agent 版本", operationId = "publishAgentVersion")
    @PostMapping("/versions/{versionId}/publish")
    public R<AgentResponse> publishVersion(@PathVariable Long versionId) {
        return R.ok(agentService.publishVersion(versionId));
    }

    @Operation(summary = "停用 Agent", operationId = "disableAgent")
    @PostMapping("/{agentId}/disable")
    public R<AgentResponse> disableAgent(@PathVariable Long agentId) {
        return R.ok(agentService.disableAgent(agentId));
    }

    @Operation(summary = "启用 Agent", operationId = "enableAgent")
    @PostMapping("/{agentId}/enable")
    public R<AgentResponse> enableAgent(@PathVariable Long agentId) {
        return R.ok(agentService.enableAgent(agentId));
    }

    // ==================== LLM Models ====================

    @Operation(summary = "查询 AI 服务商列表", operationId = "listAiProviders")
    @GetMapping("/ai-providers")
    public R<List<AiProviderResponse>> listAiProviders() {
        return R.ok(agentService.listAiProviders());
    }

    @Operation(summary = "创建 AI 服务商", operationId = "createAiProvider")
    @PostMapping("/ai-providers")
    public R<AiProviderResponse> createAiProvider(@Valid @RequestBody SaveAiProviderRequest request) {
        return R.ok(agentService.createAiProvider(request));
    }

    @Operation(summary = "更新 AI 服务商", operationId = "updateAiProvider")
    @PutMapping("/ai-providers/{providerId}")
    public R<AiProviderResponse> updateAiProvider(@PathVariable Long providerId,
                                                  @Valid @RequestBody SaveAiProviderRequest request) {
        return R.ok(agentService.updateAiProvider(providerId, request));
    }

    @Operation(summary = "删除 AI 服务商", operationId = "deleteAiProvider")
    @DeleteMapping("/ai-providers/{providerId}")
    public R<Void> deleteAiProvider(@PathVariable Long providerId) {
        agentService.deleteAiProvider(providerId);
        return R.ok();
    }

    @Operation(summary = "测试 AI 服务商连通性", operationId = "testAiProvider")
    @PostMapping("/ai-providers/{providerId}/test")
    public R<ModelTestResponse> testAiProvider(@PathVariable Long providerId) {
        return R.ok(agentService.testAiProvider(providerId));
    }

    @Operation(summary = "查询服务商可用模型", operationId = "listAiProviderModels")
    @GetMapping("/ai-providers/{providerId}/models")
    public R<List<ProviderModelOptionResponse>> listProviderModels(@PathVariable Long providerId) {
        return R.ok(agentService.listProviderModels(providerId));
    }

    @Operation(summary = "查询 LLM 模型列表", operationId = "listLlmModels")
    @GetMapping("/llm-models")
    public R<List<LlmModelResponse>> listLlmModels() {
        return R.ok(agentService.listLlmModels());
    }

    @Operation(summary = "创建 LLM 模型", operationId = "createLlmModel")
    @PostMapping("/llm-models")
    public R<LlmModelResponse> createLlmModel(@Valid @RequestBody CreateLlmModelRequest request) {
        return R.ok(agentService.createLlmModel(request));
    }

    @Operation(summary = "测试模型草稿连通性", operationId = "testLlmModelDraft")
    @PostMapping("/llm-models/test")
    public R<ModelTestResponse> testModelDraft(@Valid @RequestBody TestLlmModelRequest request) {
        return R.ok(agentService.testModelConnection(request));
    }

    @Operation(summary = "更新 LLM 模型", operationId = "updateLlmModel")
    @PutMapping("/llm-models/{modelId}")
    public R<LlmModelResponse> updateLlmModel(@PathVariable Long modelId,
                                               @Valid @RequestBody UpdateLlmModelRequest request) {
        return R.ok(agentService.updateLlmModel(modelId, request));
    }

    @Operation(summary = "删除 LLM 模型", operationId = "deleteLlmModel")
    @DeleteMapping("/llm-models/{modelId}")
    public R<Void> deleteLlmModel(@PathVariable Long modelId) {
        agentService.deleteLlmModel(modelId);
        return R.ok();
    }

    @Operation(summary = "测试模型连通性", operationId = "testLlmModel")
    @PostMapping("/llm-models/{modelId}/test")
    public R<ModelTestResponse> testModel(@PathVariable Long modelId) {
        return R.ok(agentService.testModelConnection(modelId));
    }

    // ==================== Trial Run ====================

    @Operation(summary = "Agent 试运行", operationId = "trialRunAgent")
    @PostMapping("/trial-run")
    public R<TrialRunResponse> trialRun(@Valid @RequestBody TrialRunRequest request) {
        return R.ok(agentService.trialRun(request));
    }

    @Operation(summary = "运行 Agent 工作流", operationId = "runAgentWorkflow")
    @PostMapping("/workflow-run")
    public R<WorkflowRunResponse> runWorkflow(@Valid @RequestBody WorkflowRunRequest request) {
        return R.ok(agentService.runWorkflow(request));
    }
}
