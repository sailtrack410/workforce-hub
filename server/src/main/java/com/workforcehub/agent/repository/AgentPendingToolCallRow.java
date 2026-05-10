package com.workforcehub.agent.repository;

/**
 * 待人工确认的 Agent 工具调用日志。
 */
public record AgentPendingToolCallRow(
    Long logId,
    Long taskId,
    Long toolId,
    String requestPayloadJson,
    String toolCode,
    String toolName,
    String toolType,
    String riskLevel,
    boolean readOnlyFlag,
    String approvalMode
) {
}
