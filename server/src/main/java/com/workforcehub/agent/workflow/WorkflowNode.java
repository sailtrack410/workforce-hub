package com.workforcehub.agent.workflow;

import com.fasterxml.jackson.databind.JsonNode;

public record WorkflowNode(
    String id,
    WorkflowNodeType type,
    JsonNode config,
    String outputKey,
    JsonNode outputFormat
) {
}
