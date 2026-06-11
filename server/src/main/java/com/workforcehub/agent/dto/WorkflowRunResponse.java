package com.workforcehub.agent.dto;

import java.util.List;

public record WorkflowRunResponse(
    boolean success,
    String finalReply,
    List<StepTrace> steps,
    String errorMessage,
    long durationMs
) {
    public record StepTrace(
        String nodeId,
        String type,
        String status,
        String output,
        String nextNodeId,
        long durationMs
    ) {}
}
