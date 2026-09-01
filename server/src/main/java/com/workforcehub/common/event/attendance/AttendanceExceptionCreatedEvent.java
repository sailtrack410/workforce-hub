package com.workforcehub.common.event.attendance;

import com.workforcehub.common.event.DomainEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 新建考勤异常事件，字段为插件事件所需的完整白名单快照。
 */
public record AttendanceExceptionCreatedEvent(
    Long exceptionId,
    Long dailyResultId,
    Long userId,
    LocalDate workDate,
    String exceptionType,
    String exceptionDesc,
    String status,
    Long operatorId,
    LocalDateTime occurredAt
) implements DomainEvent {

    public static final String EVENT_NAME = "attendance.exception.created";

    @Override
    public String eventName() {
        return EVENT_NAME;
    }
}
