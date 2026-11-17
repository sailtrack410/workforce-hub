package com.workforcehub.plugin.sdk;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvocationContextBinderTest {

    private final InvocationContextBinder binder = new InvocationContextBinder();

    @Test
    void eventCallbackShouldBindEventIdentity() {
        DomainEvent event = new DomainEvent(
            "event-1",
            EventTypes.WORKFLOW_APPROVED,
            1L,
            1L,
            "workflow-1",
            "42",
            Map.of()
        );

        InvocationContext context = binder.forEventCallback("plugin-a", event);

        assertEquals("plugin-a", context.getPluginId());
        assertEquals(42L, context.getUserId());
        assertEquals(InvocationContext.CallerType.PLUGIN, context.getCallerType());
        assertEquals("event-1", context.getRequestId());
        assertEquals(InvocationContext.TriggerSource.EVENT_CALLBACK, context.getSource());
    }

    @Test
    void systemEventCallbackShouldHaveNoUserId() {
        DomainEvent event = new DomainEvent(
            "event-2",
            EventTypes.FILE_UPLOADED,
            1L,
            1L,
            "file-1",
            "system",
            Map.of()
        );

        InvocationContext context = binder.forEventCallback("plugin-a", event);

        assertNull(context.getUserId());
    }

    @Test
    void eventCallbackShouldRejectUnknownTriggerIdentity() {
        DomainEvent event = new DomainEvent(
            "event-3",
            EventTypes.FILE_UPLOADED,
            1L,
            1L,
            "file-2",
            "unknown",
            Map.of()
        );

        assertThrows(IllegalArgumentException.class,
            () -> binder.forEventCallback("plugin-a", event));
    }
}
