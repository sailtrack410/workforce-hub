package com.workforcehub.plugin.runtime;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.workforcehub.common.event.attendance.AttendanceExceptionCreatedEvent;
import com.workforcehub.plugin.sdk.DomainEvent;
import com.workforcehub.plugin.sdk.EventTypes;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PluginAttendanceExceptionEventListenerTest {

    @Test
    void shouldDispatchExactWhitelistedPayload() {
        PluginEventDispatcher dispatcher = mock(PluginEventDispatcher.class);
        PluginAttendanceExceptionEventListener listener =
            new PluginAttendanceExceptionEventListener(dispatcher);
        AttendanceExceptionCreatedEvent source = event();

        listener.onAttendanceExceptionCreated(source);

        var eventCaptor = org.mockito.ArgumentCaptor.forClass(DomainEvent.class);
        verify(dispatcher).dispatch(eventCaptor.capture());
        DomainEvent event = eventCaptor.getValue();
        assertEquals(EventTypes.ATTENDANCE_EXCEPTION, event.eventType());
        assertEquals("attendance-exception-9001", event.eventId());
        assertEquals("attendance-exception-9001", event.idempotencyKey());
        assertEquals(1L, event.version());
        assertEquals("2003", event.triggeredBy());
        assertTrue(event.occurredAt() > 0);
        assertEquals(Set.of(
            "exceptionId",
            "dailyResultId",
            "userId",
            "workDate",
            "exceptionType",
            "exceptionDesc",
            "status"
        ), event.payload().keySet());
        assertEquals(9001L, event.payload().get("exceptionId"));
        assertEquals(8001L, event.payload().get("dailyResultId"));
        assertEquals(2003L, event.payload().get("userId"));
        assertEquals("2026-05-08", event.payload().get("workDate"));
        assertEquals("LATE", event.payload().get("exceptionType"));
        assertEquals("迟到20分钟", event.payload().get("exceptionDesc"));
        assertEquals("PENDING", event.payload().get("status"));
    }

    @Test
    void nonFatalThrowableMustNotEscapeListener() {
        Logger logger = (Logger) LoggerFactory.getLogger(PluginAttendanceExceptionEventListener.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        PluginEventDispatcher dispatcher = mock(PluginEventDispatcher.class);
        doThrow(new AssertionError("assertion failure"), new LinkageError("linkage failure"))
            .when(dispatcher)
            .dispatch(any(DomainEvent.class));
        PluginAttendanceExceptionEventListener listener =
            new PluginAttendanceExceptionEventListener(dispatcher);

        try {
            assertDoesNotThrow(() -> listener.onAttendanceExceptionCreated(event()));
            assertDoesNotThrow(() -> listener.onAttendanceExceptionCreated(event()));
            verify(dispatcher, times(2)).dispatch(any(DomainEvent.class));

            ILoggingEvent assertionLog = appender.list.get(0);
            assertNotNull(assertionLog.getThrowableProxy());
            assertEquals(AssertionError.class.getName(), assertionLog.getThrowableProxy().getClassName());
            assertEquals("考勤异常插件事件分发失败: exceptionId=9001", assertionLog.getFormattedMessage());

            ILoggingEvent linkageLog = appender.list.get(1);
            assertNotNull(linkageLog.getThrowableProxy());
            assertEquals(LinkageError.class.getName(), linkageLog.getThrowableProxy().getClassName());
            assertEquals("考勤异常插件事件分发失败: exceptionId=9001", linkageLog.getFormattedMessage());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private AttendanceExceptionCreatedEvent event() {
        return new AttendanceExceptionCreatedEvent(
            9001L,
            8001L,
            2003L,
            LocalDate.of(2026, 5, 8),
            "LATE",
            "迟到20分钟",
            "PENDING",
            2003L,
            LocalDateTime.of(2026, 5, 8, 9, 20)
        );
    }
}
