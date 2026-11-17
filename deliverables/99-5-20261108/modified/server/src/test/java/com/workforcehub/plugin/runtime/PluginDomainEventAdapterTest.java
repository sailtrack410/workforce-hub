package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.DomainEvent;
import com.workforcehub.plugin.sdk.EventTypes;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginDomainEventAdapterTest {

    private final PluginDomainEventAdapter adapter = new PluginDomainEventAdapter();

    @Test
    void shouldAdaptHostEventToSdkEvent() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 27, 9, 30);
        HostEvent source = new HostEvent(42L, occurredAt);
        Map<String, Object> payload = Map.of("userId", 100L);

        DomainEvent event = adapter.adapt(
            source,
            EventTypes.EMPLOYEE_HIRED,
            "event-1",
            "employee-hired-100",
            payload
        );

        assertEquals("event-1", event.eventId());
        assertEquals(EventTypes.EMPLOYEE_HIRED, event.eventType());
        assertEquals(occurredAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), event.occurredAt());
        assertEquals(1L, event.version());
        assertEquals("employee-hired-100", event.idempotencyKey());
        assertEquals("42", event.triggeredBy());
        assertEquals(payload, event.payload());
    }

    @Test
    void shouldUseSystemWhenHostEventHasNoOperator() {
        HostEvent source = new HostEvent(null, LocalDateTime.of(2026, 7, 27, 10, 0));

        DomainEvent event = adapter.adapt(
            source,
            EventTypes.FILE_UPLOADED,
            "event-2",
            "file-uploaded-1",
            Map.of()
        );

        assertEquals("system", event.triggeredBy());
    }

    private record HostEvent(Long operatorId, LocalDateTime occurredAt)
            implements com.workforcehub.common.event.DomainEvent {

        @Override
        public String eventName() {
            return "host.test";
        }
    }
}
