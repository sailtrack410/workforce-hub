package com.workforcehub.agent.repository;

/**
 * 已超过人工确认截止时间的 Agent 任务。
 */
public record AgentExpiredTaskRow(
    Long taskId
) {
}
