package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginEventSubscriptionRepository;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.sdk.DomainEvent;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.InvocationContextBinder;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * 同步分发宿主领域事件到已订阅插件。
 */
@Component
public class PluginEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PluginEventDispatcher.class);

    private final PluginEventSubscriptionRepository subscriptionRepository;
    private final PluginManagerService pluginManagerService;
    private final PluginLifecycleGuard lifecycleGuard;
    private final PluginPermissionService permissionService;
    private final InvocationContextBinder contextBinder;

    public PluginEventDispatcher(
            PluginEventSubscriptionRepository subscriptionRepository,
            PluginManagerService pluginManagerService,
            PluginLifecycleGuard lifecycleGuard,
            PluginPermissionService permissionService,
            InvocationContextBinder contextBinder) {
        this.subscriptionRepository = subscriptionRepository;
        this.pluginManagerService = pluginManagerService;
        this.lifecycleGuard = lifecycleGuard;
        this.permissionService = permissionService;
        this.contextBinder = contextBinder;
    }

    public void dispatch(DomainEvent event) {
        for (var subscription : subscriptionRepository.findByEventType(event.eventType())) {
            String pluginId = subscription.pluginId();
            boolean acquired = false;

            try {
                if (pluginManagerService.getPlugin(pluginId).getStatus() != PluginStatus.ACTIVE) {
                    continue;
                }
                acquired = lifecycleGuard.tryAcquire(pluginId);
                if (!acquired) {
                    continue;
                }

                permissionService.checkPluginCapability(pluginId, "EVENT_SUBSCRIBE");
                permissionService.checkPluginCapability(pluginId, "EVENT_SUBSCRIBE_" + event.eventType());

                Optional<PluginActionHandler> handler =
                    pluginManagerService.findActionHandler(pluginId, subscription.actionCode());
                if (handler.isEmpty()) {
                    continue;
                }

                InvocationContext context = contextBinder.forEventCallback(pluginId, event);
                handler.get().execute(context, Map.of(
                    "eventId", event.eventId(),
                    "eventType", event.eventType(),
                    "occurredAt", event.occurredAt(),
                    "version", event.version(),
                    "idempotencyKey", event.idempotencyKey(),
                    "triggeredBy", event.triggeredBy(),
                    "payload", event.payload()
                ));
            } catch (PluginPermissionService.PermissionDeniedException | NoSuchElementException e) {
                log.debug(
                    "插件事件分发已跳过: pluginId={}, eventType={}, actionCode={}, reason={}",
                    pluginId,
                    event.eventType(),
                    subscription.actionCode(),
                    e.getMessage()
                );
            } catch (Exception e) {
                log.warn(
                    "插件事件分发失败: pluginId={}, eventType={}, actionCode={}",
                    pluginId,
                    event.eventType(),
                    subscription.actionCode(),
                    e
                );
            } finally {
                if (acquired) {
                    lifecycleGuard.release(pluginId);
                }
            }
        }
    }
}
