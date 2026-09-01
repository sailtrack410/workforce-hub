package com.workforcehub.plugin.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.audit.AuditLogWriter;
import com.workforcehub.plugin.sdk.ErrorCodes;
import com.workforcehub.plugin.sdk.InvocationContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 插件宿主边界脱敏审计。
 */
@Service
public class PluginAuditService {

    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;

    public PluginAuditService(AuditLogWriter auditLogWriter, ObjectMapper objectMapper) {
        this.auditLogWriter = auditLogWriter;
        this.objectMapper = objectMapper;
    }

    public void writeAction(
            InvocationContext context,
            String pluginId,
            String actionCode,
            int serviceCode,
            boolean retryable) {
        Operator operator = operator(context, pluginId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("pluginId", pluginId);
        detail.put("actionCode", actionCode);
        detail.put("triggerSource", context.getSource().name());
        detail.put("serviceCode", serviceCode);
        detail.put("retryable", retryable);
        auditLogWriter.write(
            operator.type(),
            operator.id(),
            "PLUGIN_ACTION_EXECUTED",
            "PLUGIN_ACTION",
            null,
            serviceCode == 0 ? "SUCCESS" : "FAIL",
            json(detail)
        );
    }

    public void writeActionFailure(
            InvocationContext context,
            String pluginId,
            String actionCode) {
        writeAction(context, pluginId, actionCode, ErrorCodes.INTERNAL_ERROR, false);
    }

    public void writeEventSubscription(
            InvocationContext context,
            String eventType,
            String actionCode,
            boolean subscribed) {
        Objects.requireNonNull(context, "context");
        String pluginId = Objects.requireNonNull(context.getPluginId(), "pluginId");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("pluginId", pluginId);
        detail.put("eventType", eventType);
        detail.put("actionCode", actionCode);
        detail.put("triggerSource", context.getSource().name());
        auditLogWriter.write(
            "SYSTEM",
            null,
            subscribed ? "PLUGIN_EVENT_SUBSCRIBED" : "PLUGIN_EVENT_UNSUBSCRIBED",
            "PLUGIN_EVENT_SUBSCRIPTION",
            null,
            "SUCCESS",
            json(detail)
        );
    }

    private Operator operator(InvocationContext context, String pluginId) {
        Objects.requireNonNull(context, "context");
        if (!Objects.equals(pluginId, context.getPluginId())) {
            throw new IllegalArgumentException("插件审计上下文不匹配");
        }
        if (context.getCallerType() == InvocationContext.CallerType.USER) {
            return new Operator("USER", Objects.requireNonNull(context.getUserId(), "userId"));
        }
        if (context.getCallerType() == InvocationContext.CallerType.ADMIN) {
            return new Operator("ADMIN", Objects.requireNonNull(context.getUserId(), "userId"));
        }
        return new Operator("SYSTEM", null);
    }

    private String json(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("插件审计序列化失败", e);
        }
    }

    private record Operator(String type, Long id) {
    }
}
