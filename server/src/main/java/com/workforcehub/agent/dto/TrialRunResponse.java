package com.workforcehub.agent.dto;

public record TrialRunResponse(
    boolean success,
    String outputJson,
    String errorMessage,
    long durationMs
) {}
