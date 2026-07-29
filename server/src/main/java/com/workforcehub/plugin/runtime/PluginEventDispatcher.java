package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginEventSubscriptionRepository;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.sdk.ActionDescriptor;
import com.workforcehub.plugin.sdk.DomainEvent;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.InvocationContextBinder;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.plugin.sdk.ServiceResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 同步分发宿主领域事件到已订阅插件。
 * 当前入口只同步读取订阅并投递到有界宿主线程池，插件回调异步执行。
 */
@Component
public class PluginEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PluginEventDispatcher.class);
    private static final int CALLBACK_QUEUE_CAPACITY = 256;

    private final PluginEventSubscriptionRepository subscriptionRepository;
    private final PluginManagerService pluginManagerService;
    private final PluginLifecycleGuard lifecycleGuard;
    private final PluginPermissionService permissionService;
    private final InvocationContextBinder contextBinder;
    private final Executor callbackExecutor;
    private final ScheduledExecutorService timeoutScheduler;

    @Autowired
    public PluginEventDispatcher(
            PluginEventSubscriptionRepository subscriptionRepository,
            PluginManagerService pluginManagerService,
            PluginLifecycleGuard lifecycleGuard,
            PluginPermissionService permissionService,
            InvocationContextBinder contextBinder) {
        this(
            subscriptionRepository,
            pluginManagerService,
            lifecycleGuard,
            permissionService,
            contextBinder,
            createCallbackExecutor(),
            createTimeoutScheduler()
        );
    }

    PluginEventDispatcher(
            PluginEventSubscriptionRepository subscriptionRepository,
            PluginManagerService pluginManagerService,
            PluginLifecycleGuard lifecycleGuard,
            PluginPermissionService permissionService,
            InvocationContextBinder contextBinder,
            Executor callbackExecutor,
            ScheduledExecutorService timeoutScheduler) {
        this.subscriptionRepository = subscriptionRepository;
        this.pluginManagerService = pluginManagerService;
        this.lifecycleGuard = lifecycleGuard;
        this.permissionService = permissionService;
        this.contextBinder = contextBinder;
        this.callbackExecutor = callbackExecutor;
        this.timeoutScheduler = timeoutScheduler;
    }

    public void dispatch(DomainEvent event) {
        var subscriptions = subscriptionRepository.findByEventType(event.eventType());
        for (var subscription : subscriptions) {
            try {
                callbackExecutor.execute(() -> dispatchToSubscriber(
                    event,
                    subscription.pluginId(),
                    subscription.actionCode()
                ));
            } catch (RejectedExecutionException e) {
                log.warn(
                    "插件事件队列已满，已丢弃: pluginId={}, eventType={}, actionCode={}, eventId={}",
                    subscription.pluginId(),
                    event.eventType(),
                    subscription.actionCode(),
                    event.eventId()
                );
            } catch (Throwable e) {
                rethrowIfFatal(e);
                log.warn(
                    "插件事件投递失败: pluginId={}, eventType={}, actionCode={}, eventId={}",
                    subscription.pluginId(),
                    event.eventType(),
                    subscription.actionCode(),
                    event.eventId(),
                    e
                );
            }
        }
    }

    private void dispatchToSubscriber(DomainEvent event, String pluginId, String actionCode) {
        Thread callbackThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = callbackThread.getContextClassLoader();
        boolean originallyInterrupted = callbackThread.isInterrupted();
        boolean acquired = false;

        try {
            if (pluginManagerService.getPlugin(pluginId).getStatus() != PluginStatus.ACTIVE) {
                return;
            }
            acquired = lifecycleGuard.tryAcquire(pluginId);
            if (!acquired) {
                return;
            }

            permissionService.checkPluginCapability(pluginId, "EVENT_SUBSCRIBE");
            permissionService.checkPluginCapability(pluginId, "EVENT_SUBSCRIBE_" + event.eventType());

            Optional<PluginActionHandler> handler =
                pluginManagerService.findActionHandler(pluginId, actionCode);
            if (handler.isEmpty()) {
                return;
            }

            ActionDescriptor descriptor = handler.get().getDescriptor();
            if (!descriptor.idempotent()) {
                log.warn(
                    "插件事件处理器非幂等，已跳过: pluginId={}, eventType={}, actionCode={}",
                    pluginId,
                    event.eventType(),
                    actionCode
                );
                return;
            }

            InvocationContext context = contextBinder.forEventCallback(pluginId, event);
            ServiceResult<Object> result = executeWithTimeout(
                handler.get(),
                descriptor.defaultTimeout(),
                context,
                event,
                pluginId,
                actionCode
            );
            if (result == null) {
                log.warn(
                    "插件事件处理器返回 null: pluginId={}, eventType={}, actionCode={}, eventId={}",
                    pluginId,
                    event.eventType(),
                    actionCode,
                    event.eventId()
                );
            } else if (result.getCode() != 0) {
                log.warn(
                    "插件事件处理失败: pluginId={}, eventType={}, actionCode={}, eventId={}, code={}, retryable={}",
                    pluginId,
                    event.eventType(),
                    actionCode,
                    event.eventId(),
                    result.getCode(),
                    result.isRetryable()
                );
            }
        } catch (PluginPermissionService.PermissionDeniedException | NoSuchElementException e) {
            log.debug(
                "插件事件分发已跳过: pluginId={}, eventType={}, actionCode={}, reason={}",
                pluginId,
                event.eventType(),
                actionCode,
                e.getMessage()
            );
        } catch (Throwable e) {
            rethrowIfFatal(e);
            log.warn(
                "插件事件分发失败: pluginId={}, eventType={}, actionCode={}",
                pluginId,
                event.eventType(),
                actionCode,
                e
            );
        } finally {
            try {
                callbackThread.setContextClassLoader(originalContextClassLoader);
            } catch (Throwable e) {
                rethrowIfFatal(e);
                log.warn("插件事件回调线程上下文恢复失败: pluginId={}", pluginId, e);
            }
            if (originallyInterrupted) {
                callbackThread.interrupt();
            } else {
                Thread.interrupted();
            }
            if (acquired) {
                try {
                    lifecycleGuard.release(pluginId);
                } catch (Throwable e) {
                    rethrowIfFatal(e);
                    log.warn("插件事件生命周期计数释放失败: pluginId={}", pluginId, e);
                }
            }
        }
    }

    private ServiceResult<Object> executeWithTimeout(
            PluginActionHandler handler,
            Duration timeout,
            InvocationContext context,
            DomainEvent event,
            String pluginId,
            String actionCode) {
        Thread callbackThread = Thread.currentThread();
        Object timeoutLock = new Object();
        boolean[] running = {true};
        ScheduledFuture<?> timeoutFuture = timeoutScheduler.schedule(() -> {
            synchronized (timeoutLock) {
                if (!running[0]) {
                    return;
                }
                running[0] = false;
                log.warn(
                    "插件事件处理超时，已中断回调线程: pluginId={}, eventType={}, actionCode={}, eventId={}, timeoutMs={}",
                    pluginId,
                    event.eventType(),
                    actionCode,
                    event.eventId(),
                    timeout.toMillis()
                );
                callbackThread.interrupt();
            }
        }, Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);

        try {
            return handler.execute(context, Map.of(
                "eventId", event.eventId(),
                "eventType", event.eventType(),
                "occurredAt", event.occurredAt(),
                "version", event.version(),
                "idempotencyKey", event.idempotencyKey(),
                "triggeredBy", event.triggeredBy(),
                "payload", event.payload()
            ));
        } finally {
            synchronized (timeoutLock) {
                running[0] = false;
                timeoutFuture.cancel(false);
            }
        }
    }

    @PreDestroy
    void shutdown() {
        if (callbackExecutor instanceof ExecutorService executorService) {
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(
                        PluginLifecycleGuard.DEFAULT_DRAIN_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS)) {
                    log.warn("插件事件回调线程池关闭超时，仍有插件代码在运行");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("等待插件事件回调线程池关闭时被中断");
            }
        }
        timeoutScheduler.shutdownNow();
    }

    private static ExecutorService createCallbackExecutor() {
        int threadCount = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
        return new ThreadPoolExecutor(
            threadCount,
            threadCount,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(CALLBACK_QUEUE_CAPACITY),
            daemonThreadFactory("plugin-event-callback"),
            new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private static ScheduledExecutorService createTimeoutScheduler() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(
            1,
            daemonThreadFactory("plugin-event-timeout")
        );
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            thread.setContextClassLoader(PluginEventDispatcher.class.getClassLoader());
            return thread;
        };
    }

    private static void rethrowIfFatal(Throwable throwable) {
        if (throwable instanceof VirtualMachineError error) {
            throw error;
        }
        if (throwable instanceof ThreadDeath threadDeath) {
            throw threadDeath;
        }
    }
}
