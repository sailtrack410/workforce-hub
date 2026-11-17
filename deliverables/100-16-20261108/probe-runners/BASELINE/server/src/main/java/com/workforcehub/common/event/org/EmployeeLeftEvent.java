package com.workforcehub.common.event.org;

import com.workforcehub.common.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * 员工正式离职事件
 */
public record EmployeeLeftEvent(
    Long userId,
    String employeeNo,
    Long primaryDeptId,
    Long positionId,
    Long managerUserId,
    String beforeEmploymentStatus,
    String afterEmploymentStatus,
    Long operatorId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public static final String EVENT_NAME = "org.employee.left";

    @Override
    public String eventName() {
        return EVENT_NAME;
    }
}
