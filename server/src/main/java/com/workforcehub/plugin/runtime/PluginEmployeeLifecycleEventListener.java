package com.workforcehub.plugin.runtime;

import com.workforcehub.common.event.org.EmployeeHiredEvent;
import com.workforcehub.common.event.org.EmployeeLeftEvent;
import com.workforcehub.plugin.sdk.DomainEvent;
import com.workforcehub.plugin.sdk.EventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将员工入职、离职领域事件桥接到插件事件分发器。
 */
@Component
public class PluginEmployeeLifecycleEventListener {

    private static final Logger log = LoggerFactory.getLogger(PluginEmployeeLifecycleEventListener.class);

    private final PluginEventDispatcher eventDispatcher;
    private final PluginDomainEventAdapter eventAdapter = new PluginDomainEventAdapter();

    public PluginEmployeeLifecycleEventListener(PluginEventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
    }

    @EventListener
    public void onEmployeeHired(EmployeeHiredEvent event) {
        try {
            String eventId = "employee-hired-" + event.userId();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", event.userId());
            payload.put("employeeNo", event.employeeNo());
            payload.put("primaryDeptId", event.primaryDeptId());
            payload.put("positionId", event.positionId());
            payload.put("managerUserId", event.managerUserId());
            payload.put("employmentStatus", event.employmentStatus());
            payload.put("hireDate", event.hireDate() == null ? null : event.hireDate().toString());

            DomainEvent pluginEvent = eventAdapter.adapt(
                event,
                EventTypes.EMPLOYEE_HIRED,
                eventId,
                eventId,
                payload
            );
            eventDispatcher.dispatch(pluginEvent);
        } catch (Throwable failure) {
            handleFailure(EventTypes.EMPLOYEE_HIRED, event.userId(), failure);
        }
    }

    @EventListener
    public void onEmployeeLeft(EmployeeLeftEvent event) {
        try {
            String eventId = "employee-left-" + event.userId();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", event.userId());
            payload.put("employeeNo", event.employeeNo());
            payload.put("primaryDeptId", event.primaryDeptId());
            payload.put("positionId", event.positionId());
            payload.put("managerUserId", event.managerUserId());
            payload.put("beforeEmploymentStatus", event.beforeEmploymentStatus());
            payload.put("afterEmploymentStatus", event.afterEmploymentStatus());

            DomainEvent pluginEvent = eventAdapter.adapt(
                event,
                EventTypes.EMPLOYEE_LEFT,
                eventId,
                eventId,
                payload
            );
            eventDispatcher.dispatch(pluginEvent);
        } catch (Throwable failure) {
            handleFailure(EventTypes.EMPLOYEE_LEFT, event.userId(), failure);
        }
    }

    private void handleFailure(String eventType, Long userId, Throwable failure) {
        if (failure instanceof VirtualMachineError error) {
            throw error;
        }
        if (failure instanceof ThreadDeath threadDeath) {
            throw threadDeath;
        }
        log.warn(
            "员工生命周期插件事件分发失败: eventType={}, userId={}",
            eventType,
            userId,
            failure
        );
    }
}
