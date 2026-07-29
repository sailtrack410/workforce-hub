package com.workforcehub.common.event.org;

import com.workforcehub.common.event.DomainEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工正式入职事件
 */
public record EmployeeHiredEvent(
    Long userId,
    String employeeNo,
    Long primaryDeptId,
    Long positionId,
    Long managerUserId,
    String employmentStatus,
    LocalDate hireDate,
    Long operatorId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public static final String EVENT_NAME = "org.employee.hired";

    @Override
    public String eventName() {
        return EVENT_NAME;
    }
}
