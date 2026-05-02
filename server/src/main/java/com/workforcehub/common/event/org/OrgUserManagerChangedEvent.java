package com.workforcehub.common.event.org;

import com.workforcehub.common.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * 业务用户直属上级变化事件
 */
public record OrgUserManagerChangedEvent(
    Long userId,
    String employeeNo,
    Long primaryDeptId,
    Long beforeManagerUserId,
    Long afterManagerUserId,
    Long operatorId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public static final String EVENT_NAME = "org.user.manager-changed";

    @Override
    public String eventName() {
        return EVENT_NAME;
    }
}
