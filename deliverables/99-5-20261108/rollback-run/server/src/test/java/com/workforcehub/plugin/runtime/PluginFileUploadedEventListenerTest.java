package com.workforcehub.plugin.runtime;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.workforcehub.common.event.file.FileUploadedEvent;
import com.workforcehub.plugin.sdk.DomainEvent;
import com.workforcehub.plugin.sdk.EventTypes;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PluginFileUploadedEventListenerTest {

    @Test
    void dispatcherFailureMustNotEscapeListener() {
        Logger logger = (Logger) LoggerFactory.getLogger(PluginFileUploadedEventListener.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        PluginEventDispatcher dispatcher = mock(PluginEventDispatcher.class);
        doThrow(new IllegalStateException("dispatcher failure"))
            .when(dispatcher)
            .dispatch(any(DomainEvent.class));
        PluginFileUploadedEventListener listener = new PluginFileUploadedEventListener(dispatcher);

        FileUploadedEvent event = new FileUploadedEvent(
            9001L,
            "PERSONAL_FILE",
            null,
            "employee.txt",
            "text/plain",
            12L,
            "USER",
            2003L,
            "PENDING",
            2003L,
            LocalDateTime.of(2026, 7, 28, 10, 0)
        );

        try {
            assertDoesNotThrow(() -> listener.onFileUploaded(event));
            var eventCaptor = org.mockito.ArgumentCaptor.forClass(DomainEvent.class);
            verify(dispatcher).dispatch(eventCaptor.capture());
            assertEquals(EventTypes.FILE_UPLOADED, eventCaptor.getValue().eventType());

            ILoggingEvent logEvent = appender.list.get(0);
            assertNotNull(logEvent.getThrowableProxy());
            assertEquals(IllegalStateException.class.getName(), logEvent.getThrowableProxy().getClassName());
            assertEquals("文件上传插件事件分发失败: attachmentRefId=9001", logEvent.getFormattedMessage());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void dispatcherAssertionErrorMustNotEscapeListener() {
        PluginEventDispatcher dispatcher = mock(PluginEventDispatcher.class);
        doThrow(new AssertionError("dispatcher failure"))
            .when(dispatcher)
            .dispatch(any(DomainEvent.class));
        PluginFileUploadedEventListener listener = new PluginFileUploadedEventListener(dispatcher);
        FileUploadedEvent event = new FileUploadedEvent(
            9002L,
            "PERSONAL_FILE",
            null,
            "employee.txt",
            "text/plain",
            12L,
            "USER",
            2003L,
            "PENDING",
            2003L,
            LocalDateTime.of(2026, 7, 28, 10, 0)
        );

        assertDoesNotThrow(() -> listener.onFileUploaded(event));
    }
}
