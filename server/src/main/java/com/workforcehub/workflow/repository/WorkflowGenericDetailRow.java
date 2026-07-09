package com.workforcehub.workflow.repository;

public record WorkflowGenericDetailRow(
    String processCode,
    String summary,
    String formPayloadJson
) {
}
