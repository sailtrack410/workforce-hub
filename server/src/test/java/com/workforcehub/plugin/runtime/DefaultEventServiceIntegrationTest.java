package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginEventSubscriptionRepository;
import com.workforcehub.plugin.repository.PluginOperationIdempotencyRepository;
import com.workforcehub.plugin.sdk.ActionDescriptor;
import com.workforcehub.plugin.sdk.ErrorCodes;
import com.workforcehub.plugin.sdk.EventTypes;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.plugin.sdk.ServiceResult;
import com.workforcehub.plugin.sdk.TestInvocationContextBinder;
import com.workforcehub.plugin.sdk.service.EventService;
import com.workforcehub.support.LocalIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "workforce.plugin.root=target/test-plugins")
@ActiveProfiles("api")
class DefaultEventServiceIntegrationTest extends LocalIntegrationTestSupport {

    private static final String PLUGIN_ID = "event-service-integration-plugin";
    private static final String EVENT_TYPE = EventTypes.WORKFLOW_APPROVED;
    private static final String EVENT_CAPABILITY = "EVENT_SUBSCRIBE_" + EVENT_TYPE;

    @Autowired
    private EventService eventService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private PluginManagerService pluginManagerService;

    @SpyBean
    private PluginEventSubscriptionRepository subscriptionRepository;

    @SpyBean
    private PluginOperationIdempotencyRepository idempotencyRepository;

    private InvocationContext ctx;

    @BeforeEach
    void setUpEventService() {
        cleanupPluginRows();
        jdbc.update(
            "INSERT INTO sys_plugin_capability (plugin_id, capability_code, description) VALUES (?, ?, ?)",
            PLUGIN_ID,
            "EVENT_SUBSCRIBE",
            "integration test"
        );
        jdbc.update(
            "INSERT INTO sys_plugin_capability (plugin_id, capability_code, description) VALUES (?, ?, ?)",
            PLUGIN_ID,
            EVENT_CAPABILITY,
            "integration test"
        );
        ctx = new TestInvocationContextBinder().forPluginStartup(PLUGIN_ID);
        PluginActionHandler handlerA = eventHandler("handle-a");
        PluginActionHandler handlerB = eventHandler("handle-b");
        when(pluginManagerService.findActionHandler(PLUGIN_ID, "handle-a"))
            .thenReturn(Optional.of(handlerA));
        when(pluginManagerService.findActionHandler(PLUGIN_ID, "handle-b"))
            .thenReturn(Optional.of(handlerB));
    }

    @AfterEach
    void cleanupEventService() {
        cleanupPluginRows();
    }

    @Test
    void subscribeShouldPersistSubscriptionAndIdempotencyInOneTransaction() {
        ServiceResult<Void> result = eventService.subscribe(ctx, EVENT_TYPE, "handle-a", "subscribe-1");

        assertEquals(ErrorCodes.SUCCESS, result.getCode());
        assertEquals(1, countRows("sys_plugin_event_subscription"));
        assertEquals(1, countRows("sys_plugin_operation_idempotency"));
        assertEquals("handle-a", jdbc.queryForObject(
            "SELECT action_code FROM sys_plugin_event_subscription WHERE plugin_id = ? AND event_type = ?",
            String.class,
            PLUGIN_ID,
            EVENT_TYPE
        ));
    }

    @Test
    void duplicateKeyWithDifferentActionShouldNotOverwriteOriginalSubscription() {
        assertEquals(ErrorCodes.SUCCESS,
            eventService.subscribe(ctx, EVENT_TYPE, "handle-a", "subscribe-2").getCode());

        ServiceResult<Void> duplicate =
            eventService.subscribe(ctx, EVENT_TYPE, "handle-b", "subscribe-2");

        assertEquals(ErrorCodes.DUPLICATE_OPERATION, duplicate.getCode());
        assertEquals(1, countRows("sys_plugin_event_subscription"));
        assertEquals(1, countRows("sys_plugin_operation_idempotency"));
        assertEquals("handle-a", jdbc.queryForObject(
            "SELECT action_code FROM sys_plugin_event_subscription WHERE plugin_id = ? AND event_type = ?",
            String.class,
            PLUGIN_ID,
            EVENT_TYPE
        ));
    }

    @Test
    void concurrentSameKeyAndActionShouldBothSucceed() throws Exception {
        String idempotencyKey = "subscribe-concurrent-same";
        synchronizeIdempotencyInserts(idempotencyKey);

        List<ServiceResult<Void>> results = runConcurrentSubscriptions(
            "handle-a",
            "handle-a",
            idempotencyKey
        );

        assertTrue(results.stream().allMatch(result -> result.getCode() == ErrorCodes.SUCCESS));
        assertEquals(1, countRows("sys_plugin_event_subscription"));
        assertEquals(1, countRows("sys_plugin_operation_idempotency"));
        assertEquals("handle-a", jdbc.queryForObject(
            "SELECT action_code FROM sys_plugin_event_subscription WHERE plugin_id = ? AND event_type = ?",
            String.class,
            PLUGIN_ID,
            EVENT_TYPE
        ));
    }

    @Test
    void concurrentSameKeyAndDifferentActionShouldReturnOneConflict() throws Exception {
        String idempotencyKey = "subscribe-concurrent-different";
        synchronizeIdempotencyInserts(idempotencyKey);

        List<ServiceResult<Void>> results = runConcurrentSubscriptions(
            "handle-a",
            "handle-b",
            idempotencyKey
        );

        assertEquals(1L, results.stream()
            .filter(result -> result.getCode() == ErrorCodes.SUCCESS)
            .count());
        assertEquals(1L, results.stream()
            .filter(result -> result.getCode() == ErrorCodes.DUPLICATE_OPERATION)
            .count());
        assertEquals(1, countRows("sys_plugin_event_subscription"));
        assertEquals(1, countRows("sys_plugin_operation_idempotency"));
        String expectedAction = results.get(0).getCode() == ErrorCodes.SUCCESS ? "handle-a" : "handle-b";
        assertEquals(expectedAction, jdbc.queryForObject(
            "SELECT action_code FROM sys_plugin_event_subscription WHERE plugin_id = ? AND event_type = ?",
            String.class,
            PLUGIN_ID,
            EVENT_TYPE
        ));
    }

    @Test
    void subscriptionFailureShouldRollBackIdempotencyClaim() {
        doThrow(new DataAccessResourceFailureException("forced subscription failure"))
            .when(subscriptionRepository).save(PLUGIN_ID, EVENT_TYPE, "handle-a");

        assertThrows(DataAccessResourceFailureException.class,
            () -> eventService.subscribe(ctx, EVENT_TYPE, "handle-a", "subscribe-3"));

        assertEquals(0, countRows("sys_plugin_event_subscription"));
        assertEquals(0, countRows("sys_plugin_operation_idempotency"));
    }

    @Test
    void directBeanCallWithoutTotalCapabilityShouldBeDenied() {
        jdbc.update(
            "DELETE FROM sys_plugin_capability WHERE plugin_id = ? AND capability_code = ?",
            PLUGIN_ID,
            "EVENT_SUBSCRIBE"
        );

        assertThrows(PluginPermissionService.PermissionDeniedException.class,
            () -> eventService.subscribe(ctx, EVENT_TYPE, "handle-a", "subscribe-no-total"));

        assertEquals(0, countRows("sys_plugin_event_subscription"));
        assertEquals(0, countRows("sys_plugin_operation_idempotency"));
    }

    @Test
    void directBeanUnsubscribeWithoutTotalCapabilityShouldBeDenied() {
        assertEquals(ErrorCodes.SUCCESS,
            eventService.subscribe(ctx, EVENT_TYPE, "handle-a", "subscribe-before-revoke").getCode());
        jdbc.update(
            "DELETE FROM sys_plugin_capability WHERE plugin_id = ? AND capability_code = ?",
            PLUGIN_ID,
            "EVENT_SUBSCRIBE"
        );

        assertThrows(PluginPermissionService.PermissionDeniedException.class,
            () -> eventService.unsubscribe(ctx, EVENT_TYPE, "unsubscribe-no-total"));

        assertEquals(1, countRows("sys_plugin_event_subscription"));
        assertEquals(1, countRows("sys_plugin_operation_idempotency"));
    }

    private int countRows(String table) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE plugin_id = ?",
            Integer.class,
            PLUGIN_ID
        );
        return count == null ? 0 : count;
    }

    private void synchronizeIdempotencyInserts(String idempotencyKey) {
        CountDownLatch entered = new CountDownLatch(2);
        doAnswer(invocation -> {
            entered.countDown();
            assertTrue(entered.await(10, TimeUnit.SECONDS), "并发订阅未同时到达幂等写入");
            return invocation.callRealMethod();
        }).when(idempotencyRepository).insert(
            eq(PLUGIN_ID),
            eq("EVENT_SUBSCRIBE"),
            eq(idempotencyKey),
            anyString()
        );
    }

    private List<ServiceResult<Void>> runConcurrentSubscriptions(
            String firstAction,
            String secondAction,
            String idempotencyKey) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ServiceResult<Void>> first = executor.submit(() ->
                eventService.subscribe(ctx, EVENT_TYPE, firstAction, idempotencyKey)
            );
            Future<ServiceResult<Void>> second = executor.submit(() ->
                eventService.subscribe(ctx, EVENT_TYPE, secondAction, idempotencyKey)
            );
            return List.of(
                first.get(20, TimeUnit.SECONDS),
                second.get(20, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private void cleanupPluginRows() {
        jdbc.update("DELETE FROM sys_plugin_event_subscription WHERE plugin_id = ?", PLUGIN_ID);
        jdbc.update("DELETE FROM sys_plugin_operation_idempotency WHERE plugin_id = ?", PLUGIN_ID);
        jdbc.update("DELETE FROM sys_plugin_capability WHERE plugin_id = ?", PLUGIN_ID);
    }

    private PluginActionHandler eventHandler(String actionCode) {
        PluginActionHandler handler = mock(PluginActionHandler.class);
        when(handler.getDescriptor()).thenReturn(new ActionDescriptor(
            actionCode,
            "test",
            "test",
            Map.of(),
            Map.of(),
            "EVENT_SUBSCRIBE",
            Duration.ofSeconds(5),
            true
        ));
        return handler;
    }
}
