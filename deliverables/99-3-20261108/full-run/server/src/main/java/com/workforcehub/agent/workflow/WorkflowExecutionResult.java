package com.workforcehub.agent.workflow;

public record WorkflowExecutionResult(
    String replyText,
    String traceJson
) {
}
