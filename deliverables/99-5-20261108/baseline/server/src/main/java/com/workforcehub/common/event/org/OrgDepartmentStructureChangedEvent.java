package com.workforcehub.common.event.org;

import com.workforcehub.common.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * 组织部门结构变化事件
 */
public record OrgDepartmentStructureChangedEvent(
    String changeAction,
    Long deptId,
    String deptCode,
    String deptName,
    Long beforeParentId,
    Long afterParentId,
    Long beforeLeaderUserId,
    Long afterLeaderUserId,
    String beforeStatus,
    String afterStatus,
    String treePath,
    Long operatorId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public static final String EVENT_NAME = "org.department.structure-changed";

    @Override
    public String eventName() {
        return EVENT_NAME;
    }
}
