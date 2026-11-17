package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.DomainEvent;

import java.time.ZoneId;
import java.util.Map;

/**
 * 将宿主领域事件转换为插件 SDK 事件。
 */
public class PluginDomainEventAdapter {

    public DomainEvent adapt(
            com.workforcehub.common.event.DomainEvent source,
            String eventType,
            String eventId,
            String idempotencyKey,
            Map<String, Object> payload) {
        String triggeredBy = source.operatorId() == null ? "system" : source.operatorId().toString();

        return new DomainEvent(
            eventId,
            eventType,
            source.occurredAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            1L,
            idempotencyKey,
            triggeredBy,
            payload
        );
    }
}
