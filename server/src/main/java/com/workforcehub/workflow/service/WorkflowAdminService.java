package com.workforcehub.workflow.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.workflow.dto.WorkflowProcessTypeCreateRequest;
import com.workforcehub.workflow.dto.WorkflowProcessTypeResponse;
import com.workforcehub.workflow.dto.WorkflowTemplateCreateRequest;
import com.workforcehub.workflow.dto.WorkflowTemplateResponse;
import com.workforcehub.workflow.dto.WorkflowTemplateVersionCreateRequest;
import com.workforcehub.workflow.dto.WorkflowTemplateVersionPublishRequest;
import com.workforcehub.workflow.dto.WorkflowTemplateVersionResponse;
import com.workforcehub.workflow.repository.WorkflowAdminRepository;
import com.workforcehub.workflow.repository.WorkflowProcessTypeAdminRow;
import com.workforcehub.workflow.repository.WorkflowTemplateAdminRow;
import com.workforcehub.workflow.repository.WorkflowTemplateVersionAdminRow;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * module-workflow 后台流程模板治理服务。
 */
@Service
@ConditionalOnProperty(prefix = "workforce.workflow", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowAdminService {

    private final WorkflowAdminRepository workflowAdminRepository;
    private final RepositoryService repositoryService;
    private final ObjectMapper objectMapper;

    public WorkflowAdminService(WorkflowAdminRepository workflowAdminRepository,
                                RepositoryService repositoryService,
                                ObjectMapper objectMapper) {
        this.workflowAdminRepository = workflowAdminRepository;
        this.repositoryService = repositoryService;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowProcessTypeResponse createProcessType(WorkflowProcessTypeCreateRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        String processCode = trimToRequired(request.getProcessCode(), "processCode 不能为空");
        if (workflowAdminRepository.existsProcessCode(processCode)) {
            throw new BizException(409, "流程编码已存在");
        }

        Long processTypeId = IdWorker.getId();
        workflowAdminRepository.insertProcessType(
            processTypeId,
            processCode,
            trimToRequired(request.getProcessName(), "processName 不能为空"),
            blankToNull(request.getBizType()),
            normalizeStatus(request.getStatus()),
            operatorId
        );
        return toProcessTypeResponse(workflowAdminRepository.getProcessTypeOrThrow(processTypeId));
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowTemplateResponse createTemplate(WorkflowTemplateCreateRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        String templateCode = trimToRequired(request.getTemplateCode(), "templateCode 不能为空");
        if (!workflowAdminRepository.existsActiveProcessType(request.getProcessTypeId())) {
            throw new BizException(404, "流程类型不存在或未启用");
        }
        if (workflowAdminRepository.existsTemplateCode(templateCode)) {
            throw new BizException(409, "流程模板编码已存在");
        }

        Long templateId = IdWorker.getId();
        workflowAdminRepository.insertTemplate(
            templateId,
            request.getProcessTypeId(),
            templateCode,
            trimToRequired(request.getTemplateName(), "templateName 不能为空"),
            normalizeStatus(request.getStatus()),
            operatorId
        );
        return toTemplateResponse(workflowAdminRepository.getTemplateOrThrow(templateId));
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowTemplateVersionResponse createTemplateVersion(Long templateId,
                                                                WorkflowTemplateVersionCreateRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        if (!workflowAdminRepository.existsActiveTemplate(templateId)) {
            throw new BizException(404, "流程模板不存在或未启用");
        }
        if (workflowAdminRepository.existsTemplateVersion(templateId, request.getVersionNo())) {
            throw new BizException(409, "流程模板版本号已存在");
        }

        String formSchemaJson = normalizeJson(request.getFormSchemaJson(), "formSchemaJson 不是合法 JSON");
        String ruleSnapshotJson = normalizeJson(request.getRuleSnapshotJson(), "ruleSnapshotJson 不是合法 JSON");
        Long versionId = IdWorker.getId();
        workflowAdminRepository.insertTemplateVersion(
            versionId,
            templateId,
            request.getVersionNo(),
            formSchemaJson,
            ruleSnapshotJson,
            operatorId
        );
        return toVersionResponse(workflowAdminRepository.getTemplateVersionOrThrow(versionId));
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowTemplateVersionResponse publishTemplateVersion(Long versionId,
                                                                 WorkflowTemplateVersionPublishRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        WorkflowTemplateVersionAdminRow version = workflowAdminRepository.getTemplateVersionOrThrow(versionId);
        if (!"DRAFT".equals(version.status())) {
            throw new BizException(409, "只有草稿版本可以发布");
        }
        if (!workflowAdminRepository.existsActiveTemplate(version.templateId())) {
            throw new BizException(404, "流程模板不存在或未启用");
        }

        String flowableDefinitionKey = trimToRequired(
            request.getFlowableDefinitionKey(),
            "flowableDefinitionKey 不能为空"
        );
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(flowableDefinitionKey)
            .latestVersion()
            .singleResult();
        if (processDefinition == null) {
            throw new BizException(404, "Flowable流程定义不存在");
        }

        workflowAdminRepository.archiveCurrentPublishedVersions(version.templateId(), versionId, operatorId);
        workflowAdminRepository.publishTemplateVersion(
            versionId,
            flowableDefinitionKey,
            processDefinition.getId(),
            operatorId
        );
        return toVersionResponse(workflowAdminRepository.getTemplateVersionOrThrow(versionId));
    }

    public List<WorkflowTemplateResponse> listTemplates(String processCode) {
        return workflowAdminRepository.listTemplates(processCode).stream()
            .map(this::toTemplateResponse)
            .toList();
    }

    public List<WorkflowProcessTypeResponse> listProcessTypes(String processCode) {
        return workflowAdminRepository.listProcessTypes(processCode).stream()
            .map(this::toProcessTypeResponse)
            .toList();
    }

    public List<WorkflowTemplateVersionResponse> listTemplateVersions(Long templateId) {
        workflowAdminRepository.getTemplateOrThrow(templateId);
        return workflowAdminRepository.listTemplateVersions(templateId).stream()
            .map(this::toVersionResponse)
            .toList();
    }

    private WorkflowProcessTypeResponse toProcessTypeResponse(WorkflowProcessTypeAdminRow row) {
        WorkflowProcessTypeResponse response = new WorkflowProcessTypeResponse();
        response.setProcessTypeId(row.id());
        response.setProcessCode(row.processCode());
        response.setProcessName(row.processName());
        response.setBizType(row.bizType());
        response.setStatus(row.status());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
    }

    private WorkflowTemplateResponse toTemplateResponse(WorkflowTemplateAdminRow row) {
        WorkflowTemplateResponse response = new WorkflowTemplateResponse();
        response.setTemplateId(row.id());
        response.setProcessTypeId(row.processTypeId());
        response.setProcessCode(row.processCode());
        response.setProcessName(row.processName());
        response.setTemplateCode(row.templateCode());
        response.setTemplateName(row.templateName());
        response.setStatus(row.status());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        if (row.currentVersionId() != null) {
            WorkflowTemplateVersionResponse currentVersion = new WorkflowTemplateVersionResponse();
            currentVersion.setVersionId(row.currentVersionId());
            currentVersion.setTemplateId(row.id());
            currentVersion.setVersionNo(row.currentVersionNo());
            currentVersion.setFlowableDefinitionKey(row.currentFlowableDefinitionKey());
            currentVersion.setFlowableDefinitionId(row.currentFlowableDefinitionId());
            currentVersion.setStatus(row.currentVersionStatus());
            currentVersion.setEffectiveFrom(row.currentEffectiveFrom());
            currentVersion.setEffectiveTo(row.currentEffectiveTo());
            response.setCurrentVersion(currentVersion);
        }
        return response;
    }

    private WorkflowTemplateVersionResponse toVersionResponse(WorkflowTemplateVersionAdminRow row) {
        WorkflowTemplateVersionResponse response = new WorkflowTemplateVersionResponse();
        response.setVersionId(row.id());
        response.setTemplateId(row.templateId());
        response.setVersionNo(row.versionNo());
        response.setFlowableDefinitionKey(row.flowableDefinitionKey());
        response.setFlowableDefinitionId(row.flowableDefinitionId());
        response.setFormSchemaJson(compactJson(row.formSchemaJson()));
        response.setRuleSnapshotJson(compactJson(row.ruleSnapshotJson()));
        response.setPublishedBy(row.publishedBy());
        response.setPublishedAt(row.publishedAt());
        response.setEffectiveFrom(row.effectiveFrom());
        response.setEffectiveTo(row.effectiveTo());
        response.setStatus(row.status());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "ACTIVE";
        }
        String normalized = status.trim().toUpperCase();
        if (!"ACTIVE".equals(normalized) && !"INACTIVE".equals(normalized)) {
            throw new BizException(400, "status 仅支持 ACTIVE 或 INACTIVE");
        }
        return normalized;
    }

    private String normalizeJson(String value, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(value));
        } catch (Exception e) {
            throw new BizException(400, errorMessage);
        }
    }

    private String compactJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(value));
        } catch (Exception e) {
            return value;
        }
    }

    private String trimToRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(400, message);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
