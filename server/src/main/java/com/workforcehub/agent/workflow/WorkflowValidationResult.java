package com.workforcehub.agent.workflow;

import java.util.List;

public record WorkflowValidationResult(
    boolean valid,
    List<String> messages
) {
    public static WorkflowValidationResult ok() {
        return new WorkflowValidationResult(true, List.of());
    }

    public static WorkflowValidationResult failed(List<String> messages) {
        return new WorkflowValidationResult(false, List.copyOf(messages));
    }
}
