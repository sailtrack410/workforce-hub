package com.workforcehub.plugin.runtime;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.workforcehub.common.event.workflow.WorkflowCompletedEvent;
import com.workforcehub.plugin.sdk.DomainEvent;
import com.workforcehub.plugin.sdk.EventTypes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
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
import static org.mockito.Mockito.verifyNoInteractions;

class PluginWorkflowCompletedEventListenerTest {

    @Test
    void shouldDispatchApprovedAndRejectedEventsWithExactPayload() {
        PluginEventDispatcher dispatcher = mock(PluginEventDispatcher.class);
        PluginWorkflowCompletedEventListener listener = new PluginWorkflowCompletedEventListener(dispatcher);

        listener.onWorkflowCompleted(workflowEvent("APPROVED", "task-approved"));
        listener.onWorkflowCompleted(workflowEvent("REJECTED", "task-rejected"));

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(dispatcher, times(2)).dispatch(eventCaptor.capture());
        List<DomainEvent> events = eventCaptor.getAllValues();
        assertWorkflowEvent(events.get(0), EventTypes.WORKFLOW_APPROVED, "approved", "task-approved", "APPROVED");
        assertWorkflowEvent(events.get(1), EventTypes.WORKFLOW_REJECTED, "rejected", "task-rejected", "REJECTED");
    }

    @Test
    void nonFatalDispatcherFailureMustNotEscapeListener() {
        Logger logger = (Logger) LoggerFactory.getLogger(PluginWorkflowCompletedEventListener.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        PluginEventDispatcher dispatcher = mock(PluginEventDispatcher.class);
        doThrow(new LinkageError("dispatcher failure"))
            .when(dispatcher)
            .dispatch(any(DomainEvent.class));
        PluginWorkflowCompletedEventListener listener = new PluginWorkflowCompletedEventListener(dispatcher);

        try {
            assertDoesNotThrow(() -> listener.onWorkflowCompleted(workflowEvent("APPROVED", "task-failed")));

            ILoggingEvent logEvent = appender.list.get(0);
            assertNotNull(logEvent.getThrowableProxy());
            assertEquals(LinkageError.class.getName(), logEvent.getThrowableProxy().getClassName());
            assertTrue(logEvent.getThrowableProxy().getStackTraceElementProxyArray().length > 0);
            assertEquals(
                "审批插件事件分发失败: requestId=9001, taskId=task-failed",
                logEvent.getFormattedMessage()
            );
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void unsupportedTerminalStatusMustNotDispatch() {
        PluginEventDispatcher dispatcher = mock(PluginEventDispatcher.class);
        PluginWorkflowCompletedEventListener listener = new PluginWorkflowCompletedEventListener(dispatcher);

        listener.onWorkflowCompleted(workflowEvent("WITHDRAWN", "task-withdrawn"));

        verifyNoInteractions(dispatcher);
    }

    private WorkflowCompletedEvent workflowEvent(String afterStatus, String taskId) {
        return new WorkflowCompletedEvent(
            9001L,
            "WF-LEAVE-9001",
            "LEAVE",
            2003L,
            taskId,
            "IN_APPROVAL",
            afterStatus,
            2002L,
            LocalDateTime.of(2026, 7, 30, 10, 0)
        );
    }

    private void assertWorkflowEvent(DomainEvent event,
                                     String eventType,
                                     String result,
                                     String taskId,
                                     String afterStatus) {
        String stableId = "workflow-" + result + "-9001-" + taskId;
        assertEquals(eventType, event.eventType());
        assertEquals(stableId, event.eventId());
        assertEquals(stableId, event.idempotencyKey());
        assertEquals(1L, event.version());
        assertEquals("2002", event.triggeredBy());
        assertTrue(event.occurredAt() > 0);
        assertEquals(Set.of(
            "requestId",
            "requestNo",
            "bizType",
            "applicantUserId",
            "taskId",
            "beforeStatus",
            "afterStatus"
        ), event.payload().keySet());
        assertEquals(9001L, event.payload().get("requestId"));
        assertEquals("WF-LEAVE-9001", event.payload().get("requestNo"));
        assertEquals("LEAVE", event.payload().get("bizType"));
        assertEquals(2003L, event.payload().get("applicantUserId"));
        assertEquals(taskId, event.payload().get("taskId"));
        assertEquals("IN_APPROVAL", event.payload().get("beforeStatus"));
        assertEquals(afterStatus, event.payload().get("afterStatus"));
    }
}
