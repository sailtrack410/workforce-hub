package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginEventSubscriptionRepository;
import com.workforcehub.plugin.repository.PluginEventSubscriptionRow;
import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.sdk.ActionDescriptor;
import com.workforcehub.plugin.sdk.DomainEvent;
import com.workforcehub.plugin.sdk.EventTypes;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.InvocationContextBinder;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.plugin.sdk.ServiceResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
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
    private ScheduledExecutorService timeoutScheduler;

    @BeforeEach
    void setUp() {
        timeoutScheduler = Executors.newSingleThreadScheduledExecutor();
        dispatcher = new PluginEventDispatcher(
            subscriptionRepository,
            pluginManagerService,
            lifecycleGuard,
            permissionService,
            contextBinder,
            Runnable::run,
            timeoutScheduler
        );
        lenient().when(handler.getDescriptor()).thenReturn(descriptor(ACTION_CODE, Duration.ofSeconds(5)));
    }

    @AfterEach
    void tearDown() {
        timeoutScheduler.shutdownNow();
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
        when(firstHandler.getDescriptor()).thenReturn(descriptor(ACTION_CODE, Duration.ofSeconds(5)));
        when(secondHandler.getDescriptor()).thenReturn(descriptor(secondActionCode, Duration.ofSeconds(5)));
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

    @Test
    void shouldContinueAfterOneSubscriberThrowsAssertionError() {
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
        when(firstHandler.getDescriptor()).thenReturn(descriptor(ACTION_CODE, Duration.ofSeconds(5)));
        when(secondHandler.getDescriptor()).thenReturn(descriptor(secondActionCode, Duration.ofSeconds(5)));
        when(pluginManagerService.findActionHandler(PLUGIN_ID, ACTION_CODE)).thenReturn(Optional.of(firstHandler));
        when(pluginManagerService.findActionHandler(secondPluginId, secondActionCode)).thenReturn(Optional.of(secondHandler));
        when(contextBinder.forEventCallback(eq(PLUGIN_ID), eq(event))).thenReturn(context);
        InvocationContext secondContext = mock(InvocationContext.class);
        when(contextBinder.forEventCallback(eq(secondPluginId), eq(event))).thenReturn(secondContext);
        doThrow(new AssertionError("first plugin failed"))
            .when(firstHandler).execute(any(), anyMap());
        when(secondHandler.execute(any(), anyMap())).thenReturn(ServiceResult.ok(null));

        assertDoesNotThrow(() -> dispatcher.dispatch(event));

        verify(firstHandler).execute(any(), anyMap());
        verify(secondHandler).execute(any(), anyMap());
        verify(lifecycleGuard).release(PLUGIN_ID);
        verify(lifecycleGuard).release(secondPluginId);
    }

    @Test
    void shouldSkipNonIdempotentEventHandler() {
        DomainEvent event = event();
        when(subscriptionRepository.findByEventType(event.eventType()))
            .thenReturn(List.of(subscription(PLUGIN_ID, ACTION_CODE)));
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.ACTIVE));
        when(lifecycleGuard.tryAcquire(PLUGIN_ID)).thenReturn(true);
        when(pluginManagerService.findActionHandler(PLUGIN_ID, ACTION_CODE)).thenReturn(Optional.of(handler));
        when(handler.getDescriptor()).thenReturn(new ActionDescriptor(
            ACTION_CODE,
            "test",
            "test",
            Map.of(),
            Map.of(),
            "EVENT_SUBSCRIBE",
            Duration.ofSeconds(5),
            false
        ));

        dispatcher.dispatch(event);

        verify(handler, never()).execute(any(), anyMap());
        verifyNoInteractions(contextBinder);
        verify(lifecycleGuard).release(PLUGIN_ID);
    }

    @Test
    void shouldRestoreThreadContextClassLoaderAfterCallback() {
        DomainEvent event = event();
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader pluginClassLoader = new ClassLoader(original) { };
        when(subscriptionRepository.findByEventType(event.eventType()))
            .thenReturn(List.of(subscription(PLUGIN_ID, ACTION_CODE)));
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.ACTIVE));
        when(lifecycleGuard.tryAcquire(PLUGIN_ID)).thenReturn(true);
        when(pluginManagerService.findActionHandler(PLUGIN_ID, ACTION_CODE)).thenReturn(Optional.of(handler));
        when(contextBinder.forEventCallback(PLUGIN_ID, event)).thenReturn(context);
        when(handler.execute(eq(context), anyMap())).thenAnswer(invocation -> {
            Thread.currentThread().setContextClassLoader(pluginClassLoader);
            return ServiceResult.ok(null);
        });

        try {
            dispatcher.dispatch(event);

            assertSame(original, Thread.currentThread().getContextClassLoader());
            verify(lifecycleGuard).release(PLUGIN_ID);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void productionWorkerShouldNotInheritDispatchCallerContextClassLoader() throws Exception {
        DomainEvent event = event();
        ClassLoader hostClassLoader = PluginEventDispatcher.class.getClassLoader();
        ClassLoader pluginClassLoader = new ClassLoader(hostClassLoader) { };
        AtomicReference<ClassLoader> workerClassLoader = new AtomicReference<>();
        CountDownLatch executed = new CountDownLatch(1);
        when(subscriptionRepository.findByEventType(event.eventType()))
            .thenReturn(List.of(subscription(PLUGIN_ID, ACTION_CODE)));
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.ACTIVE));
        when(lifecycleGuard.tryAcquire(PLUGIN_ID)).thenReturn(true);
        when(pluginManagerService.findActionHandler(PLUGIN_ID, ACTION_CODE)).thenReturn(Optional.of(handler));
        when(contextBinder.forEventCallback(PLUGIN_ID, event)).thenReturn(context);
        when(handler.execute(eq(context), anyMap())).thenAnswer(invocation -> {
            workerClassLoader.set(Thread.currentThread().getContextClassLoader());
            executed.countDown();
            return ServiceResult.ok(null);
        });
        PluginEventDispatcher asyncDispatcher = new PluginEventDispatcher(
            subscriptionRepository,
            pluginManagerService,
            lifecycleGuard,
            permissionService,
            contextBinder
        );
        Thread caller = new Thread(() -> asyncDispatcher.dispatch(event));
        caller.setContextClassLoader(pluginClassLoader);

        try {
            caller.start();
            caller.join(2_000);
            assertFalse(caller.isAlive());
            assertTrue(executed.await(2, TimeUnit.SECONDS));
            assertSame(hostClassLoader, workerClassLoader.get());
            verify(lifecycleGuard, org.mockito.Mockito.timeout(2_000)).release(PLUGIN_ID);
        } finally {
            asyncDispatcher.shutdown();
        }
    }

    @Test
    void shouldClearTimeoutInterruptBeforeReusingWorker() throws Exception {
        DomainEvent event = event();
        List<Runnable> callbacks = new ArrayList<>();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger executionCount = new AtomicInteger();
        AtomicReference<Boolean> secondInterrupted = new AtomicReference<>();
        CountDownLatch secondExecuted = new CountDownLatch(1);
        when(subscriptionRepository.findByEventType(event.eventType()))
            .thenReturn(List.of(subscription(PLUGIN_ID, ACTION_CODE)));
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.ACTIVE));
        when(lifecycleGuard.tryAcquire(PLUGIN_ID)).thenReturn(true);
        when(pluginManagerService.findActionHandler(PLUGIN_ID, ACTION_CODE)).thenReturn(Optional.of(handler));
        when(handler.getDescriptor()).thenReturn(
            descriptor(ACTION_CODE, Duration.ofMillis(50)),
            descriptor(ACTION_CODE, Duration.ofSeconds(5))
        );
        when(contextBinder.forEventCallback(PLUGIN_ID, event)).thenReturn(context);
        when(handler.execute(eq(context), anyMap())).thenAnswer(invocation -> {
            if (executionCount.getAndIncrement() == 0) {
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return ServiceResult.fail(4002, "timeout", true);
                }
            }
            secondInterrupted.set(Thread.currentThread().isInterrupted());
            secondExecuted.countDown();
            return ServiceResult.ok(null);
        });
        PluginEventDispatcher serialDispatcher = new PluginEventDispatcher(
            subscriptionRepository,
            pluginManagerService,
            lifecycleGuard,
            permissionService,
            contextBinder,
            callbacks::add,
            scheduler
        );

        try {
            serialDispatcher.dispatch(event);
            serialDispatcher.dispatch(event);
            Thread worker = new Thread(() -> callbacks.forEach(Runnable::run));
            worker.start();
            worker.join(2_000);

            assertFalse(worker.isAlive());
            assertTrue(secondExecuted.await(0, TimeUnit.MILLISECONDS));
            assertEquals(Boolean.FALSE, secondInterrupted.get());
            verify(lifecycleGuard, org.mockito.Mockito.timeout(2_000).times(2)).release(PLUGIN_ID);
        } finally {
            serialDispatcher.shutdown();
        }
    }

    @Test
    void dispatchShouldReturnBeforeBlockingHandlerCompletes() throws Exception {
        DomainEvent event = event();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(subscriptionRepository.findByEventType(event.eventType()))
            .thenReturn(List.of(subscription(PLUGIN_ID, ACTION_CODE)));
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.ACTIVE));
        when(lifecycleGuard.tryAcquire(PLUGIN_ID)).thenReturn(true);
        when(pluginManagerService.findActionHandler(PLUGIN_ID, ACTION_CODE)).thenReturn(Optional.of(handler));
        when(contextBinder.forEventCallback(PLUGIN_ID, event)).thenReturn(context);
        when(handler.execute(eq(context), anyMap())).thenAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return ServiceResult.ok(null);
        });
        PluginEventDispatcher asyncDispatcher = new PluginEventDispatcher(
            subscriptionRepository,
            pluginManagerService,
            lifecycleGuard,
            permissionService,
            contextBinder
        );

        try {
            long startedAt = System.nanoTime();
            asyncDispatcher.dispatch(event);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertTrue(elapsedMillis < 500, "dispatch 不应等待插件回调完成");
            assertTrue(started.await(2, TimeUnit.SECONDS), "插件回调未启动");
        } finally {
            release.countDown();
            asyncDispatcher.shutdown();
        }
    }

    @Test
    void shouldInterruptHandlerAtDescriptorTimeout() throws Exception {
        DomainEvent event = event();
        CountDownLatch interrupted = new CountDownLatch(1);
        when(subscriptionRepository.findByEventType(event.eventType()))
            .thenReturn(List.of(subscription(PLUGIN_ID, ACTION_CODE)));
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.ACTIVE));
        when(lifecycleGuard.tryAcquire(PLUGIN_ID)).thenReturn(true);
        when(pluginManagerService.findActionHandler(PLUGIN_ID, ACTION_CODE)).thenReturn(Optional.of(handler));
        when(handler.getDescriptor()).thenReturn(descriptor(ACTION_CODE, Duration.ofMillis(50)));
        when(contextBinder.forEventCallback(PLUGIN_ID, event)).thenReturn(context);
        when(handler.execute(eq(context), anyMap())).thenAnswer(invocation -> {
            try {
                new CountDownLatch(1).await();
                return ServiceResult.ok(null);
            } catch (InterruptedException e) {
                interrupted.countDown();
                return ServiceResult.fail(4002, "timeout", true);
            }
        });
        PluginEventDispatcher asyncDispatcher = new PluginEventDispatcher(
            subscriptionRepository,
            pluginManagerService,
            lifecycleGuard,
            permissionService,
            contextBinder
        );

        try {
            asyncDispatcher.dispatch(event);

            assertTrue(interrupted.await(2, TimeUnit.SECONDS), "超时后应中断插件回调线程");
            verify(lifecycleGuard, org.mockito.Mockito.timeout(2_000)).release(PLUGIN_ID);
        } finally {
            asyncDispatcher.shutdown();
        }
    }

    @Test
    void shutdownShouldWaitForIgnoringHandlerToExit() throws Exception {
        DomainEvent event = event();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interruptedByShutdown = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(subscriptionRepository.findByEventType(event.eventType()))
            .thenReturn(List.of(subscription(PLUGIN_ID, ACTION_CODE)));
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin(PluginStatus.ACTIVE));
        when(lifecycleGuard.tryAcquire(PLUGIN_ID)).thenReturn(true);
        when(pluginManagerService.findActionHandler(PLUGIN_ID, ACTION_CODE)).thenReturn(Optional.of(handler));
        when(contextBinder.forEventCallback(PLUGIN_ID, event)).thenReturn(context);
        when(handler.execute(eq(context), anyMap())).thenAnswer(invocation -> {
            started.countDown();
            while (release.getCount() > 0) {
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                    interruptedByShutdown.countDown();
                }
            }
            return ServiceResult.ok(null);
        });
        PluginEventDispatcher asyncDispatcher = new PluginEventDispatcher(
            subscriptionRepository,
            pluginManagerService,
            lifecycleGuard,
            permissionService,
            contextBinder
        );
        ExecutorService shutdownExecutor = Executors.newSingleThreadExecutor();

        try {
            asyncDispatcher.dispatch(event);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            Future<?> shutdown = shutdownExecutor.submit(asyncDispatcher::shutdown);

            assertTrue(interruptedByShutdown.await(2, TimeUnit.SECONDS));
            assertFalse(shutdown.isDone(), "关闭流程应等待仍在运行的插件回调");
            release.countDown();
            shutdown.get(2, TimeUnit.SECONDS);
            verify(lifecycleGuard, org.mockito.Mockito.timeout(2_000)).release(PLUGIN_ID);
        } finally {
            release.countDown();
            asyncDispatcher.shutdown();
            shutdownExecutor.shutdownNow();
        }
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

    private ActionDescriptor descriptor(String actionCode, Duration timeout) {
        return new ActionDescriptor(
            actionCode,
            "test",
            "test",
            Map.of(),
            Map.of(),
            "EVENT_SUBSCRIBE",
            timeout,
            true
        );
    }
}
