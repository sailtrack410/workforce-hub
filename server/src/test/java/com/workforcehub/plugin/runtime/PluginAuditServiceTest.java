package com.workforcehub.plugin.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.audit.AuditLogWriter;
import com.workforcehub.plugin.sdk.DomainEvent;
import com.workforcehub.plugin.sdk.ErrorCodes;
import com.workforcehub.plugin.sdk.EventTypes;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.TestInvocationContextBinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PluginAuditServiceTest {

    private static final String PLUGIN_ID = "audit-plugin";
    private static final String ACTION_CODE = "audit.execute";

    @Mock
    private AuditLogWriter auditLogWriter;

    private ObjectMapper objectMapper;
    private PluginAuditService auditService;
    private TestInvocationContextBinder binder;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        auditService = new PluginAuditService(auditLogWriter, objectMapper);
        binder = new TestInvocationContextBinder();
    }

    @Test
    void httpActionShouldUseAuthoritativeAdminAndStrictDetailWhitelist() throws Exception {
        InvocationContext context = binder.forHttpRequest(
            PLUGIN_ID,
            42L,
            InvocationContext.CallerType.ADMIN,
            "request-sensitive-value"
        );
        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);

        auditService.writeAction(context, PLUGIN_ID, ACTION_CODE, 0, false);

        verify(auditLogWriter).write(
            eq("ADMIN"),
            eq(42L),
            eq("PLUGIN_ACTION_EXECUTED"),
            eq("PLUGIN_ACTION"),
            isNull(),
            eq("SUCCESS"),
            detailCaptor.capture()
        );
        JsonNode detail = objectMapper.readTree(detailCaptor.getValue());
        assertEquals(
            Set.of("pluginId", "actionCode", "triggerSource", "serviceCode", "retryable"),
            fields(detail)
        );
        assertEquals(PLUGIN_ID, detail.get("pluginId").asText());
        assertEquals(ACTION_CODE, detail.get("actionCode").asText());
        assertEquals("HTTP_REQUEST", detail.get("triggerSource").asText());
        assertEquals(0, detail.get("serviceCode").asInt());
        assertFalse(detail.get("retryable").asBoolean());
        assertFalse(detailCaptor.getValue().contains("request-sensitive-value"));
    }

    @Test
    void eventActionShouldUseSystemInsteadOfInferringTriggeredUserType() {
        DomainEvent event = new DomainEvent(
            "event-1",
            EventTypes.EMPLOYEE_HIRED,
            1_000L,
            1L,
            "employee-hired-1",
            "42",
            Map.of("employeeNo", "sensitive-employee")
        );
        InvocationContext context = binder.forEventCallback(PLUGIN_ID, event);
        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);

        auditService.writeAction(context, PLUGIN_ID, ACTION_CODE, 4001, true);

        verify(auditLogWriter).write(
            eq("SYSTEM"),
            isNull(),
            eq("PLUGIN_ACTION_EXECUTED"),
            eq("PLUGIN_ACTION"),
            isNull(),
            eq("FAIL"),
            detailCaptor.capture()
        );
        assertFalse(detailCaptor.getValue().contains("sensitive-employee"));
        assertFalse(detailCaptor.getValue().contains("employee-hired-1"));
    }

    @Test
    void actionFailureShouldUseInternalErrorCode() {
        InvocationContext context = binder.forHttpRequest(
            PLUGIN_ID,
            42L,
            InvocationContext.CallerType.ADMIN,
            "request-sensitive-value"
        );

        auditService.writeActionFailure(context, PLUGIN_ID, ACTION_CODE);

        verify(auditLogWriter).write(
            eq("ADMIN"),
            eq(42L),
            eq("PLUGIN_ACTION_EXECUTED"),
            eq("PLUGIN_ACTION"),
            isNull(),
            eq("FAIL"),
            org.mockito.ArgumentMatchers.contains(
                "\"serviceCode\":" + ErrorCodes.INTERNAL_ERROR)
        );
    }

    @Test
    void eventSubscriptionShouldAlwaysUseSystemAndNotRecordIdempotencyKey() throws Exception {
        InvocationContext context = binder.forHttpRequest(
            PLUGIN_ID,
            42L,
            InvocationContext.CallerType.ADMIN,
            "request-sensitive-value"
        );
        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);

        auditService.writeEventSubscription(
            context,
            EventTypes.WORKFLOW_APPROVED,
            ACTION_CODE,
            true
        );

        verify(auditLogWriter).write(
            eq("SYSTEM"),
            isNull(),
            eq("PLUGIN_EVENT_SUBSCRIBED"),
            eq("PLUGIN_EVENT_SUBSCRIPTION"),
            isNull(),
            eq("SUCCESS"),
            detailCaptor.capture()
        );
        JsonNode detail = objectMapper.readTree(detailCaptor.getValue());
        assertEquals(
            Set.of("pluginId", "eventType", "actionCode", "triggerSource"),
            fields(detail)
        );
        assertFalse(detailCaptor.getValue().contains("request-sensitive-value"));
    }

    @Test
    void mismatchedPluginContextShouldFailBeforeWritingAudit() {
        InvocationContext context = binder.forPluginStartup(PLUGIN_ID);

        assertThrows(IllegalArgumentException.class,
            () -> auditService.writeAction(context, "other-plugin", ACTION_CODE, 0, false));

        verifyNoInteractions(auditLogWriter);
    }

    private Set<String> fields(JsonNode node) {
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}
