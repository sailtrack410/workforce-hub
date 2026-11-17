package com.workforcehub.common.event.org;

import com.workforcehub.common.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * 业务用户账号变化事件
 */
public record OrgUserAccountChangedEvent(
    String changeAction,
    Long userId,
    String loginName,
    String email,
    String beforeAccountStatus,
    String afterAccountStatus,
    boolean loginNameChanged,
    boolean emailChanged,
    boolean passwordChanged,
    Long operatorId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public static final String EVENT_NAME = "org.user.account-changed";

    @Override
    public String eventName() {
        return EVENT_NAME;
    }
}
