package com.workforcehub.plugin.runtime;

import com.workforcehub.common.event.attendance.AttendanceExceptionCreatedEvent;
import com.workforcehub.plugin.sdk.DomainEvent;
import com.workforcehub.plugin.sdk.EventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将考勤异常新建事件桥接到插件事件分发器。
 */
@Component
public class PluginAttendanceExceptionEventListener {

    private static final Logger log = LoggerFactory.getLogger(PluginAttendanceExceptionEventListener.class);

    private final PluginEventDispatcher eventDispatcher;
    private final PluginDomainEventAdapter eventAdapter = new PluginDomainEventAdapter();

    public PluginAttendanceExceptionEventListener(PluginEventDispatcher eventDispatcher) {
        this.eventDispatcher = eventDispatcher;
    }

    @EventListener
    public void onAttendanceExceptionCreated(AttendanceExceptionCreatedEvent event) {
        try {
            String eventId = "attendance-exception-" + event.exceptionId();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("exceptionId", event.exceptionId());
            payload.put("dailyResultId", event.dailyResultId());
            payload.put("userId", event.userId());
            payload.put("workDate", event.workDate().toString());
            payload.put("exceptionType", event.exceptionType());
            payload.put("exceptionDesc", event.exceptionDesc());
            payload.put("status", event.status());

            DomainEvent pluginEvent = eventAdapter.adapt(
                event,
                EventTypes.ATTENDANCE_EXCEPTION,
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
                "考勤异常插件事件分发失败: exceptionId={}",
                event.exceptionId(),
                e
            );
        }
    }
}
