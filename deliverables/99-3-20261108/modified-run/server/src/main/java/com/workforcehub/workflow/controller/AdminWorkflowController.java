package com.workforcehub.workflow.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.common.response.PageResult;
import com.workforcehub.workflow.dto.AdminWorkflowRequestQuery;
import com.workforcehub.workflow.dto.WorkflowProcessTypeCreateRequest;
import com.workforcehub.workflow.dto.WorkflowProcessTypeResponse;
import com.workforcehub.workflow.dto.WorkflowRequestDetailResponse;
import com.workforcehub.workflow.dto.WorkflowRequestSummaryResponse;
import com.workforcehub.workflow.dto.WorkflowTaskActionResponse;
import com.workforcehub.workflow.dto.WorkflowTemplateCreateRequest;
import com.workforcehub.workflow.dto.WorkflowTemplateResponse;
import com.workforcehub.workflow.dto.WorkflowTemplateVersionCreateRequest;
import com.workforcehub.workflow.dto.WorkflowTemplateVersionPublishRequest;
import com.workforcehub.workflow.dto.WorkflowTemplateVersionResponse;
import com.workforcehub.workflow.service.WorkflowAdminService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台流程模板治理接口。
 */
// 流程后台接口：模板/版本/流程类型 CRUD，请求列表，审批操作
@RestController
@ConditionalOnProperty(prefix = "workforce.workflow", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/admin/workflow")
public class AdminWorkflowController {

    private final WorkflowAdminService workflowAdminService;

    public AdminWorkflowController(WorkflowAdminService workflowAdminService) {
        this.workflowAdminService = workflowAdminService;
    }

    @Operation(summary = "创建流程类型", operationId = "createWorkflowProcessType")
    @PostMapping("/process-types")
    public R<WorkflowProcessTypeResponse> createProcessType(
            @Valid @RequestBody WorkflowProcessTypeCreateRequest request) {
        return R.ok(workflowAdminService.createProcessType(request));
    }

    @Operation(summary = "分页查询审批请求", operationId = "pageWorkflowRequests")
    @GetMapping("/requests")
    public R<PageResult<WorkflowRequestSummaryResponse>> listRequests(
            @Valid AdminWorkflowRequestQuery query) {
        return R.ok(workflowAdminService.pageRequests(query));
    }

    @Operation(summary = "查询审批请求详情", operationId = "getWorkflowRequest")
    @GetMapping("/requests/{requestId}")
    public R<WorkflowRequestDetailResponse> getRequest(@PathVariable Long requestId) {
        return R.ok(workflowAdminService.getRequestDetail(requestId));
    }

    @Operation(summary = "查询请求可用操作", operationId = "listWorkflowRequestActions")
    @GetMapping("/requests/{requestId}/actions")
    public R<List<WorkflowTaskActionResponse>> listRequestActions(@PathVariable Long requestId) {
        return R.ok(workflowAdminService.listRequestActions(requestId));
    }

    @Operation(summary = "查询流程类型列表", operationId = "listWorkflowProcessTypes")
    @GetMapping("/process-types")
    public R<List<WorkflowProcessTypeResponse>> listProcessTypes(@RequestParam(required = false) String processCode) {
        return R.ok(workflowAdminService.listProcessTypes(processCode));
    }

    @Operation(summary = "创建流程模板", operationId = "createWorkflowTemplate")
    @PostMapping("/templates")
    public R<WorkflowTemplateResponse> createTemplate(
            @Valid @RequestBody WorkflowTemplateCreateRequest request) {
        return R.ok(workflowAdminService.createTemplate(request));
    }

    @Operation(summary = "查询流程模板列表", operationId = "listWorkflowTemplates")
    @GetMapping("/templates")
    public R<List<WorkflowTemplateResponse>> listTemplates(@RequestParam(required = false) String processCode) {
        return R.ok(workflowAdminService.listTemplates(processCode));
    }

    @Operation(summary = "创建模板版本", operationId = "createWorkflowTemplateVersion")
    @PostMapping("/templates/{templateId}/versions")
    public R<WorkflowTemplateVersionResponse> createTemplateVersion(
            @PathVariable Long templateId,
            @Valid @RequestBody WorkflowTemplateVersionCreateRequest request) {
        return R.ok(workflowAdminService.createTemplateVersion(templateId, request));
    }

    @Operation(summary = "查询模板版本列表", operationId = "listWorkflowTemplateVersions")
    @GetMapping("/templates/{templateId}/versions")
    public R<List<WorkflowTemplateVersionResponse>> listTemplateVersions(@PathVariable Long templateId) {
        return R.ok(workflowAdminService.listTemplateVersions(templateId));
    }

    @Operation(summary = "发布模板版本", operationId = "publishWorkflowTemplateVersion")
    @PostMapping("/template-versions/{versionId}/publish")
    public R<WorkflowTemplateVersionResponse> publishTemplateVersion(
            @PathVariable Long versionId,
            @Valid @RequestBody WorkflowTemplateVersionPublishRequest request) {
        return R.ok(workflowAdminService.publishTemplateVersion(versionId, request));
    }
}
