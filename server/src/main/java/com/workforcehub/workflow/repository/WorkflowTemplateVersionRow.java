package com.workforcehub.workflow.repository;

/**
 * 已发布流程模板版本读模型。
 */
public record WorkflowTemplateVersionRow(
    Long id,
    String flowableDefinitionKey,
    String flowableDefinitionId
) {
}
