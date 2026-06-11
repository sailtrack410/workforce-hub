package com.workforcehub.agent.workflow;

public record WorkflowEdge(
    String from,
    String to,
    Integer fromPort
) {
}
