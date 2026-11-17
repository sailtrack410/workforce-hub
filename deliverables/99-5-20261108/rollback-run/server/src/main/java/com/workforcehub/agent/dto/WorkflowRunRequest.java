package com.workforcehub.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record WorkflowRunRequest(
    @NotBlank String inputJson,
    Long versionId,
    String workflowConfigJson,
    String systemPrompt,
    String modelConfigJson
) {}
