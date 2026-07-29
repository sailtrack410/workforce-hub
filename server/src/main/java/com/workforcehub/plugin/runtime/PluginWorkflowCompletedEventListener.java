package com.workforcehub.plugin.runtime;

import com.workforcehub.common.event.workflow.WorkflowCompletedEvent;
import com.workforcehub.plugin.sdk.DomainEvent;
import com.workforcehub.plugin.sdk.EventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将人工审批完成事件桥接到插件事件分发器。
 */
@Component
public class PluginWorkflowCompletedEventListener {

    private static final Logger log = LoggerFactory.getLogger(PluginWorkflowCompletedEventListener.class);

    private final PluginEventDispatcher eventDispatcher;
    private final PluginDomainEventAdapter eventAdapter = new PluginDomainEventAdapter();

    public PluginWorkflowCompletedEventListener(PluginEventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
    }

    @EventListener
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        try {
            String eventType;
            String result;
            switch (event.afterStatus()) {
                case "APPROVED" -> {
                    eventType = EventTypes.WORKFLOW_APPROVED;
                    result = "approved";
                }
                case "REJECTED" -> {
                    eventType = EventTypes.WORKFLOW_REJECTED;
                    result = "rejected";
                }
                default -> {
                    log.warn(
                        "审批完成事件状态不受支持，已跳过: requestId={}, taskId={}, afterStatus={}",
                        event.requestId(),
                        event.taskId(),
                        event.afterStatus()
                    );
                    return;
                }
            }
            String eventId = "workflow-" + result + "-" + event.requestId() + "-" + event.taskId();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestId", event.requestId());
            payload.put("requestNo", event.requestNo());
            payload.put("bizType", event.bizType());
            payload.put("applicantUserId", event.applicantUserId());
            payload.put("taskId", event.taskId());
            payload.put("beforeStatus", event.beforeStatus());
            payload.put("afterStatus", event.afterStatus());

            DomainEvent pluginEvent = eventAdapter.adapt(
                event,
                eventType,
                eventId,
                eventId,
                payload
            );
            eventDispatcher.dispatch(pluginEvent);
        } catch (Throwable e) {
            if (e instanceof VirtualMachineError fatal) {
                throw fatal;
            }
            if (e instanceof ThreadDeath fatal) {
                throw fatal;
            }
            log.warn(
                "审批插件事件分发失败: requestId={}, taskId={}",
                event.requestId(),
                event.taskId(),
                e
            );
        }
    }
}
