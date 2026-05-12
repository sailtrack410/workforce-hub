package com.workforcehub.workflow.controller;

import com.workforcehub.common.response.R;
import com.workforcehub.workflow.dto.WorkflowProcessTypeCreateRequest;
import com.workforcehub.workflow.dto.WorkflowProcessTypeResponse;
import com.workforcehub.workflow.dto.WorkflowTemplateCreateRequest;
import com.workforcehub.workflow.dto.WorkflowTemplateResponse;
import com.workforcehub.workflow.dto.WorkflowTemplateVersionCreateRequest;
import com.workforcehub.workflow.dto.WorkflowTemplateVersionPublishRequest;
import com.workforcehub.workflow.dto.WorkflowTemplateVersionResponse;
import com.workforcehub.workflow.service.WorkflowAdminService;
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
@RestController
@ConditionalOnProperty(prefix = "workforce.workflow", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/admin/workflow")
public class AdminWorkflowController {

    private final WorkflowAdminService workflowAdminService;

    public AdminWorkflowController(WorkflowAdminService workflowAdminService) {
        this.workflowAdminService = workflowAdminService;
    }

    @PostMapping("/process-types")
    public R<WorkflowProcessTypeResponse> createProcessType(
            @Valid @RequestBody WorkflowProcessTypeCreateRequest request) {
        return R.ok(workflowAdminService.createProcessType(request));
    }

    @GetMapping("/process-types")
    public R<List<WorkflowProcessTypeResponse>> listProcessTypes(@RequestParam(required = false) String processCode) {
        return R.ok(workflowAdminService.listProcessTypes(processCode));
    }

    @PostMapping("/templates")
    public R<WorkflowTemplateResponse> createTemplate(
            @Valid @RequestBody WorkflowTemplateCreateRequest request) {
        return R.ok(workflowAdminService.createTemplate(request));
    }

    @GetMapping("/templates")
    public R<List<WorkflowTemplateResponse>> listTemplates(@RequestParam(required = false) String processCode) {
        return R.ok(workflowAdminService.listTemplates(processCode));
    }

    @PostMapping("/templates/{templateId}/versions")
    public R<WorkflowTemplateVersionResponse> createTemplateVersion(
            @PathVariable Long templateId,
            @Valid @RequestBody WorkflowTemplateVersionCreateRequest request) {
        return R.ok(workflowAdminService.createTemplateVersion(templateId, request));
    }

    @GetMapping("/templates/{templateId}/versions")
    public R<List<WorkflowTemplateVersionResponse>> listTemplateVersions(@PathVariable Long templateId) {
        return R.ok(workflowAdminService.listTemplateVersions(templateId));
    }

    @PostMapping("/template-versions/{versionId}/publish")
    public R<WorkflowTemplateVersionResponse> publishTemplateVersion(
            @PathVariable Long versionId,
            @Valid @RequestBody WorkflowTemplateVersionPublishRequest request) {
        return R.ok(workflowAdminService.publishTemplateVersion(versionId, request));
    }
}
