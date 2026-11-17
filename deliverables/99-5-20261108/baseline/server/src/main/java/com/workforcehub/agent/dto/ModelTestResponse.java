package com.workforcehub.agent.dto;

public record ModelTestResponse(
    boolean success,
    String message,
    String sampleReply,
    long durationMs
) {}
