package com.workforcehub.plugin.eventprobe;

import com.workforcehub.plugin.sdk.ActionDescriptor;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.plugin.sdk.ServiceResult;
import org.pf4j.Extension;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Extension
public final class EventProbeHandler implements PluginActionHandler {

    public static final String ACTION_CODE = "event.probe.capture";

    private static final AtomicInteger INVOCATION_COUNT = new AtomicInteger();
    private static final AtomicReference<Map<String, Object>> LAST_EVENT = new AtomicReference<>();
    private static final AtomicReference<Map<String, Object>> LAST_CONTEXT = new AtomicReference<>();

    @Override
    public ActionDescriptor getDescriptor() {
        return new ActionDescriptor(
            ACTION_CODE,
            "Event probe",
            "Captures one host event for integration verification",
            Map.of(),
            Map.of(),
            "EVENT_SUBSCRIBE_EMPLOYEE_HIRED",
            Duration.ofSeconds(2),
            true
        );
    }

    @Override
    public ServiceResult<Object> execute(InvocationContext ctx, Map<String, Object> params) {
        LAST_CONTEXT.set(Map.of(
            "pluginId", ctx.getPluginId(),
            "userId", ctx.getUserId(),
            "callerType", ctx.getCallerType().name(),
            "requestId", ctx.getRequestId(),
            "source", ctx.getSource().name()
        ));
        LAST_EVENT.set(Map.copyOf(params));
        INVOCATION_COUNT.incrementAndGet();
        return ServiceResult.ok(Map.of("eventId", params.get("eventId")));
    }

    public int getInvocationCount() {
        return INVOCATION_COUNT.get();
    }

    public Map<String, Object> getLastEvent() {
        return LAST_EVENT.get();
    }

    public Map<String, Object> getLastContext() {
        return LAST_CONTEXT.get();
    }
}
