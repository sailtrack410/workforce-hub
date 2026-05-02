package com.workforcehub.common.event;

import java.time.LocalDateTime;

/**
 * 内部领域事件统一约定
 */
public interface DomainEvent {

    String eventName();

    Long operatorId();

    LocalDateTime occurredAt();
}
