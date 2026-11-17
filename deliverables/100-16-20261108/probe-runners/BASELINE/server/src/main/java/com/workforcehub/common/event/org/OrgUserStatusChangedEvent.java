package com.workforcehub.common.event.org;

import com.workforcehub.common.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * 业务用户状态变化事件
 */
public record OrgUserStatusChangedEvent(
    String statusType,
    Long userId,
    String employeeNo,
    String beforeStatus,
    String afterStatus,
    String employmentStatus,
    String recordStatus,
    String accountStatus,
    Long operatorId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public static final String EVENT_NAME = "org.user.status-changed";

    @Override
    public String eventName() {
        return EVENT_NAME;
    }
}
