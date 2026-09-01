package com.workforcehub.plugin.runtime;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.workforcehub.common.event.org.EmployeeHiredEvent;
import com.workforcehub.common.event.org.EmployeeLeftEvent;
import com.workforcehub.plugin.sdk.DomainEvent;
import com.workforcehub.plugin.sdk.EventTypes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PluginEmployeeLifecycleEventListenerTest {

    @Test
    void hiredPayloadShouldMatchV1ContractAndRetainNulls() {
        PluginEventDispatcher dispatcher = mock(PluginEventDispatcher.class);
        PluginEmployeeLifecycleEventListener listener = new PluginEmployeeLifecycleEventListener(dispatcher);
        EmployeeHiredEvent source = new EmployeeHiredEvent(
            2001L,
            "E2001",
            111L,
            null,
            null,
            "PROBATION",
            LocalDate.of(2026, 8, 1),
            1001L,
            LocalDateTime.of(2026, 7, 30, 9, 0)
        );

        listener.onEmployeeHired(source);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(dispatcher).dispatch(captor.capture());
        DomainEvent event = captor.getValue();
        assertEquals("employee-hired-2001", event.eventId());
        assertEquals("employee-hired-2001", event.idempotencyKey());
        assertEquals(EventTypes.EMPLOYEE_HIRED, event.eventType());
        assertEquals("1001", event.triggeredBy());
        assertEquals(Set.of(
            "userId",
            "employeeNo",
            "primaryDeptId",
            "positionId",
            "managerUserId",
            "employmentStatus",
            "hireDate"
        ), event.payload().keySet());
        assertEquals(2001L, event.payload().get("userId"));
        assertEquals("E2001", event.payload().get("employeeNo"));
        assertEquals(111L, event.payload().get("primaryDeptId"));
        assertTrue(event.payload().containsKey("positionId"));
        assertNull(event.payload().get("positionId"));
        assertTrue(event.payload().containsKey("managerUserId"));
        assertNull(event.payload().get("managerUserId"));
        assertEquals("PROBATION", event.payload().get("employmentStatus"));
        assertEquals("2026-08-01", event.payload().get("hireDate"));
    }

    @Test
    void leftPayloadShouldMatchV1ContractAndRetainNulls() {
        PluginEventDispatcher dispatcher = mock(PluginEventDispatcher.class);
        PluginEmployeeLifecycleEventListener listener = new PluginEmployeeLifecycleEventListener(dispatcher);
        EmployeeLeftEvent source = new EmployeeLeftEvent(
            2002L,
            "E2002",
            112L,
            3001L,
            null,
            "PENDING_OFFBOARD",
            "OFFBOARDED",
            null,
            LocalDateTime.of(2026, 7, 30, 10, 0)
        );

        listener.onEmployeeLeft(source);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(dispatcher).dispatch(captor.capture());
        DomainEvent event = captor.getValue();
        assertEquals("employee-left-2002", event.eventId());
        assertEquals("employee-left-2002", event.idempotencyKey());
        assertEquals(EventTypes.EMPLOYEE_LEFT, event.eventType());
        assertEquals("system", event.triggeredBy());
        assertEquals(Set.of(
            "userId",
            "employeeNo",
            "primaryDeptId",
            "positionId",
            "managerUserId",
            "beforeEmploymentStatus",
            "afterEmploymentStatus"
        ), event.payload().keySet());
        assertEquals(2002L, event.payload().get("userId"));
        assertEquals("E2002", event.payload().get("employeeNo"));
        assertEquals(112L, event.payload().get("primaryDeptId"));
        assertEquals(3001L, event.payload().get("positionId"));
        assertTrue(event.payload().containsKey("managerUserId"));
        assertNull(event.payload().get("managerUserId"));
        assertEquals("PENDING_OFFBOARD", event.payload().get("beforeEmploymentStatus"));
        assertEquals("OFFBOARDED", event.payload().get("afterEmploymentStatus"));
    }

    @Test
    void nonFatalThrowableMustNotEscapeAndMustKeepStackTrace() {
        Logger logger = (Logger) LoggerFactory.getLogger(PluginEmployeeLifecycleEventListener.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        PluginEventDispatcher dispatcher = mock(PluginEventDispatcher.class);
        doThrow(new AssertionError("dispatcher failure"))
            .when(dispatcher)
            .dispatch(any(DomainEvent.class));
        PluginEmployeeLifecycleEventListener listener = new PluginEmployeeLifecycleEventListener(dispatcher);
        EmployeeLeftEvent source = new EmployeeLeftEvent(
            2003L,
            "E2003",
            111L,
            null,
            null,
            "PENDING_OFFBOARD",
            "OFFBOARDED",
            1001L,
            LocalDateTime.of(2026, 7, 30, 11, 0)
        );

        try {
            assertDoesNotThrow(() -> listener.onEmployeeLeft(source));
            ILoggingEvent logEvent = appender.list.get(0);
            assertEquals(
                "员工生命周期插件事件分发失败: eventType=EMPLOYEE_LEFT, userId=2003",
                logEvent.getFormattedMessage()
            );
            assertNotNull(logEvent.getThrowableProxy());
            assertEquals(AssertionError.class.getName(), logEvent.getThrowableProxy().getClassName());
            assertTrue(logEvent.getThrowableProxy().getStackTraceElementProxyArray().length > 0);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void linkageErrorMustNotEscapeListener() {
        PluginEventDispatcher dispatcher = mock(PluginEventDispatcher.class);
        doThrow(new LinkageError("plugin linkage failure"))
            .when(dispatcher)
            .dispatch(any(DomainEvent.class));
        PluginEmployeeLifecycleEventListener listener = new PluginEmployeeLifecycleEventListener(dispatcher);
        EmployeeHiredEvent source = new EmployeeHiredEvent(
            2004L,
            "E2004",
            111L,
            null,
            null,
            "ACTIVE",
            null,
            1001L,
            LocalDateTime.of(2026, 7, 30, 12, 0)
        );

        assertDoesNotThrow(() -> listener.onEmployeeHired(source));
        verify(dispatcher).dispatch(any(DomainEvent.class));
    }
}
