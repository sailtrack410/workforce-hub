package com.workforcehub.common.event.workflow;

import com.workforcehub.common.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * 人工审批任务完成事件。
 */
public record WorkflowCompletedEvent(
    Long requestId,
    String requestNo,
    String bizType,
    Long applicantUserId,
    String taskId,
    String beforeStatus,
    String afterStatus,
    Long operatorId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public static final String EVENT_NAME = "workflow.completed";

    @Override
    public String eventName() {
        return EVENT_NAME;
    }
}
