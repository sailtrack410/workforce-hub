package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginEventSubscriptionRepository;
import com.workforcehub.plugin.repository.PluginEventSubscriptionRow;
import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.sdk.DomainEvent;
import com.workforcehub.plugin.sdk.EventTypes;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.InvocationContextBinder;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.plugin.sdk.ServiceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginEventDispatcherTest {

    private static final String PLUGIN_ID = "plugin-a";
    private static final String ACTION_CODE = "handle.workflow.approved";

    @Mock private PluginEventSubscriptionRepository subscriptionRepository;
    @Mock private PluginManagerService pluginManagerService;
    @Mock private PluginLifecycleGuard lifecycleGuard;
    @Mock private PluginPermissionService permissionService;
    @Mock private InvocationContextBinder contextBinder;
    @Mock private PluginActionHandler handler;
    @Mock private InvocationContext context;

    private PluginEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new PluginEventDispatcher(
            subscriptionRepository,
            pluginManagerService,
            lifecycleGuard,
            permissionService,
            contextBinder
        );
    }

    @Test
    void shouldDispatchFullEnvelopeToActiveAuthorizedSubscriber() {
        DomainEvent event = new DomainEvent(
            "event-1",
            EventTypes.WORKFLOW_APPROVED,
            1_000L,
            1L,
            "workflow-approved-1",
            "42",
            Map.of("workflowId", 100L)
        );
        PluginEventSubscriptionRow subscription = new PluginEventSubscriptionRow(
            PLUGIN_ID,
            event.eventType(),
            ACTION_CODE,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        PluginRow plugin = new PluginRow();
        plugin.setPluginId(PLUGIN_ID);
        plugin.setStatus(PluginStatus.ACTIVE);

        when(subscriptionRepository.findByEventType(event.eventType())).thenReturn(List.of(subscription));
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin);
        when(lifecycleGuard.tryAcquire(PLUGIN_ID)).thenReturn(true);
        when(pluginManagerService.findActionHandler(PLUGIN_ID, ACTION_CODE)).thenReturn(Optional.of(handler));
        when(contextBinder.forEventCallback(PLUGIN_ID, event)).thenReturn(context);
        when(handler.execute(eq(context), anyMap())).thenReturn(ServiceResult.ok(null));

        dispatcher.dispatch(event);

        verify(permissionService).checkPluginCapability(PLUGIN_ID, "EVENT_SUBSCRIBE");
        verify(permissionService).checkPluginCapability(
            PLUGIN_ID,
            "EVENT_SUBSCRIBE_" + EventTypes.WORKFLOW_APPROVED
        );
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(handler).execute(eq(context), paramsCaptor.capture());
        assertEquals(event.eventId(), paramsCaptor.getValue().get("eventId"));
        assertEquals(event.eventType(), paramsCaptor.getValue().get("eventType"));
        assertEquals(event.occurredAt(), paramsCaptor.getValue().get("occurredAt"));
        assertEquals(event.version(), paramsCaptor.getValue().get("version"));
        assertEquals(event.idempotencyKey(), paramsCaptor.getValue().get("idempotencyKey"));
        assertEquals(event.triggeredBy(), paramsCaptor.getValue().get("triggeredBy"));
        assertEquals(event.payload(), paramsCaptor.getValue().get("payload"));
        verify(lifecycleGuard).release(PLUGIN_ID);
    }

    @Test
    void shouldSkipStoppedPluginBeforeAcquiringLifecycleGuard() {
        DomainEvent event = event();
        when(subscriptionRepository.findByEventType(event.eventType()))
            .thenReturn(List.of(subscription(PLUGIN_ID, ACTION_CODE)));
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.STOPPED));

        dispatcher.dispatch(event);

        verifyNoInteractions(lifecycleGuard, permissionService, contextBinder, handler);
    }

    @Test
    void shouldSkipWhenLifecycleGuardIsClosed() {
        DomainEvent event = event();
        when(subscriptionRepository.findByEventType(event.eventType()))
            .thenReturn(List.of(subscription(PLUGIN_ID, ACTION_CODE)));
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.ACTIVE));
        when(lifecycleGuard.tryAcquire(PLUGIN_ID)).thenReturn(false);

        dispatcher.dispatch(event);

        verifyNoInteractions(permissionService, contextBinder, handler);
        verify(pluginManagerService, never()).findActionHandler(any(), any());
        verify(lifecycleGuard, never()).release(any());
    }

    @Test
    void shouldReleaseWhenSubscribedActionIsMissing() {
        DomainEvent event = event();
        when(subscriptionRepository.findByEventType(event.eventType()))
            .thenReturn(List.of(subscription(PLUGIN_ID, ACTION_CODE)));
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.ACTIVE));
        when(lifecycleGuard.tryAcquire(PLUGIN_ID)).thenReturn(true);
        when(pluginManagerService.findActionHandler(PLUGIN_ID, ACTION_CODE)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> dispatcher.dispatch(event));

        verify(permissionService).checkPluginCapability(PLUGIN_ID, "EVENT_SUBSCRIBE");
        verify(permissionService).checkPluginCapability(
            PLUGIN_ID, "EVENT_SUBSCRIBE_" + EventTypes.WORKFLOW_APPROVED);
        verify(lifecycleGuard).release(PLUGIN_ID);
        verifyNoInteractions(contextBinder, handler);
    }

    @Test
    void shouldSkipWhenEventCapabilityHasBeenRevoked() {
        DomainEvent event = event();
        when(subscriptionRepository.findByEventType(event.eventType()))
            .thenReturn(List.of(subscription(PLUGIN_ID, ACTION_CODE)));
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.ACTIVE));
        when(lifecycleGuard.tryAcquire(PLUGIN_ID)).thenReturn(true);
        doNothing().when(permissionService).checkPluginCapability(PLUGIN_ID, "EVENT_SUBSCRIBE");
        doThrow(new PluginPermissionService.PermissionDeniedException("revoked"))
            .when(permissionService)
            .checkPluginCapability(PLUGIN_ID, "EVENT_SUBSCRIBE_" + EventTypes.WORKFLOW_APPROVED);

        assertDoesNotThrow(() -> dispatcher.dispatch(event));

        verify(pluginManagerService, never()).findActionHandler(any(), any());
        verifyNoInteractions(contextBinder, handler);
        verify(lifecycleGuard).release(PLUGIN_ID);
    }

    @Test
    void shouldContinueAfterOneSubscriberThrows() {
        String secondPluginId = "plugin-b";
        String secondActionCode = "handle.workflow.approved.second";
        DomainEvent event = event();
        when(subscriptionRepository.findByEventType(event.eventType())).thenReturn(List.of(
            subscription(PLUGIN_ID, ACTION_CODE),
            subscription(secondPluginId, secondActionCode)
        ));
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.ACTIVE));
        when(pluginManagerService.getPlugin(secondPluginId)).thenReturn(plugin(secondPluginId, PluginStatus.ACTIVE));
        when(lifecycleGuard.tryAcquire(any())).thenReturn(true);

        PluginActionHandler firstHandler = mock(PluginActionHandler.class);
        PluginActionHandler secondHandler = mock(PluginActionHandler.class);
        when(pluginManagerService.findActionHandler(PLUGIN_ID, ACTION_CODE)).thenReturn(Optional.of(firstHandler));
        when(pluginManagerService.findActionHandler(secondPluginId, secondActionCode)).thenReturn(Optional.of(secondHandler));
        when(contextBinder.forEventCallback(eq(PLUGIN_ID), eq(event))).thenReturn(context);
        InvocationContext secondContext = mock(InvocationContext.class);
        when(contextBinder.forEventCallback(eq(secondPluginId), eq(event))).thenReturn(secondContext);
        doThrow(new RuntimeException("first plugin failed"))
            .when(firstHandler).execute(any(), anyMap());
        when(secondHandler.execute(any(), anyMap())).thenReturn(ServiceResult.ok(null));

        assertDoesNotThrow(() -> dispatcher.dispatch(event));

        verify(firstHandler).execute(any(), anyMap());
        verify(secondHandler).execute(any(), anyMap());
        verify(lifecycleGuard).release(PLUGIN_ID);
        verify(lifecycleGuard).release(secondPluginId);
    }

    @Test
    void shouldIgnoreMissingPluginAndContinue() {
        String secondPluginId = "plugin-b";
        DomainEvent event = event();
        when(subscriptionRepository.findByEventType(event.eventType())).thenReturn(List.of(
            subscription(PLUGIN_ID, ACTION_CODE),
            subscription(secondPluginId, ACTION_CODE)
        ));
        when(pluginManagerService.getPlugin(PLUGIN_ID))
            .thenThrow(new NoSuchElementException("missing"));
        when(pluginManagerService.getPlugin(secondPluginId))
            .thenReturn(plugin(secondPluginId, PluginStatus.ACTIVE));
        when(lifecycleGuard.tryAcquire(secondPluginId)).thenReturn(true);
        when(pluginManagerService.findActionHandler(secondPluginId, ACTION_CODE))
            .thenReturn(Optional.of(handler));
        when(contextBinder.forEventCallback(secondPluginId, event)).thenReturn(context);
        when(handler.execute(any(), anyMap())).thenReturn(ServiceResult.ok(null));

        assertDoesNotThrow(() -> dispatcher.dispatch(event));

        verify(handler).execute(any(), anyMap());
        verify(lifecycleGuard).release(secondPluginId);
    }

    private DomainEvent event() {
        return new DomainEvent(
            "event-1",
            EventTypes.WORKFLOW_APPROVED,
            1_000L,
            1L,
            "workflow-approved-1",
            "42",
            Map.of("workflowId", 100L)
        );
    }

    private PluginEventSubscriptionRow subscription(String pluginId, String actionCode) {
        return new PluginEventSubscriptionRow(
            pluginId,
            EventTypes.WORKFLOW_APPROVED,
            actionCode,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    private PluginRow plugin(PluginStatus status) {
        return plugin(PLUGIN_ID, status);
    }

    private PluginRow plugin(String pluginId, PluginStatus status) {
        PluginRow plugin = new PluginRow();
        plugin.setPluginId(pluginId);
        plugin.setStatus(status);
        return plugin;
    }
}
