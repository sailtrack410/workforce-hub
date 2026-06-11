package com.workforcehub.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record TrialRunRequest(
    @NotBlank String nodeId,
    String inputJson,
    String workflowConfigJson,
    String systemPrompt,
    String modelConfigJson
) {}
