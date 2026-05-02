package com.workforcehub.common.event.org;

import com.workforcehub.common.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * 业务用户创建事件
 */
public record OrgUserCreatedEvent(
    Long userId,
    String employeeNo,
    String realName,
    Long primaryDeptId,
    Long positionId,
    Long managerUserId,
    String employmentStatus,
    String recordStatus,
    Long operatorId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public static final String EVENT_NAME = "org.user.created";

    @Override
    public String eventName() {
        return EVENT_NAME;
    }
}
