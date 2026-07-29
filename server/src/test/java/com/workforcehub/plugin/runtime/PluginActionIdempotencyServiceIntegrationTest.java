package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginActionIdempotencyRepository;
import com.workforcehub.plugin.sdk.ActionDescriptor;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.plugin.sdk.ServiceResult;
import com.workforcehub.plugin.sdk.TestInvocationContextBinder;
import com.workforcehub.support.LocalIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "workforce.plugin.root=target/test-plugins")
@ActiveProfiles("api")
class PluginActionIdempotencyServiceIntegrationTest extends LocalIntegrationTestSupport {

    private static final String PLUGIN_ID = "action-idempotency-plugin";
    private static final String ACTION_CODE = "action.idempotency.execute";

    @Autowired
    private PluginActionIdempotencyService service;

    @Autowired
    private PluginActionIdempotencyCleanupJob cleanupJob;

    @Autowired
    private JdbcTemplate jdbc;

    @SpyBean
    private PluginActionIdempotencyRepository repository;

    private TestInvocationContextBinder binder;
    private InvocationContext ctx;

    @BeforeEach
    void setUpIdempotency() {
        cleanupRows();
        binder = new TestInvocationContextBinder();
        ctx = binder.forHttpRequest(
            PLUGIN_ID,
            1001L,
            InvocationContext.CallerType.ADMIN,
            "request-1"
        );
    }

    @AfterEach
    void tearDownIdempotency() {
        cleanupRows();
    }

    @Test
    void sameRequestShouldReplayAndIgnoreObjectKeyOrder() {
        AtomicInteger executions = new AtomicInteger();
        PluginActionHandler handler = handler(params -> {
            executions.incrementAndGet();
            return ServiceResult.ok(Map.of("value", "done"));
        });
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", Map.of("y", 2, "x", 1));
        first.put("a", true);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", true);
        second.put("b", Map.of("x", 1, "y", 2));

        ServiceResult<Object> initial = service.execute(
            PLUGIN_ID, ACTION_CODE, "replay-key", ctx, first, handler);
        ServiceResult<Object> replay = service.execute(
            PLUGIN_ID, ACTION_CODE, "replay-key", ctx, second, handler);

        assertEquals(0, initial.getCode());
        assertEquals(initial.getData(), replay.getData());
        assertEquals(1, executions.get());
        assertEquals("COMPLETED", status("replay-key", 1001L, ACTION_CODE));
    }

    @Test
    void concurrentReplayOfCompletedRequestShouldNotDeadlock() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        PluginActionHandler handler = handler(params -> {
            executions.incrementAndGet();
            return ServiceResult.ok(Map.of("value", "done"));
        });
        service.execute(PLUGIN_ID, ACTION_CODE, "concurrent-replay", ctx, Map.of(), handler);

        int replayCount = 8;
        CountDownLatch ready = new CountDownLatch(replayCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(replayCount);
        List<Future<ServiceResult<Object>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < replayCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return service.execute(
                        PLUGIN_ID, ACTION_CODE, "concurrent-replay", ctx, Map.of(), handler);
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<ServiceResult<Object>> future : futures) {
                assertEquals(Map.of("value", "done"), future.get(10, TimeUnit.SECONDS).getData());
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, executions.get());
    }

    @Test
    void claimShouldRetryWhenConflictingRowDisappearsBeforeRead() {
        jdbc.update("""
            INSERT INTO sys_plugin_action_idempotency
                (plugin_id, action_code, caller_type, caller_id, idempotency_key,
                 request_hash, status, claim_token, expires_at)
            VALUES (?, ?, 'ADMIN', 1001, 'disappearing-claim', ?, 'IN_PROGRESS', ?, NULL)
            """, PLUGIN_ID, ACTION_CODE, "a".repeat(64), java.util.UUID.randomUUID().toString());
        doAnswer(invocation -> {
            jdbc.update("""
                DELETE FROM sys_plugin_action_idempotency
                WHERE plugin_id = ? AND idempotency_key = ?
                """, PLUGIN_ID, "disappearing-claim");
            return Optional.empty();
        }).when(repository).findForUpdate(
            eq(PLUGIN_ID),
            eq(ACTION_CODE),
            eq("ADMIN"),
            eq(1001L),
            eq("disappearing-claim")
        );
        AtomicInteger executions = new AtomicInteger();
        PluginActionHandler handler = handler(params -> {
            executions.incrementAndGet();
            return ServiceResult.ok(Map.of("value", "done"));
        });

        ServiceResult<Object> result = service.execute(
            PLUGIN_ID, ACTION_CODE, "disappearing-claim", ctx, Map.of(), handler);

        assertEquals(0, result.getCode());
        assertEquals(1, executions.get());
        assertEquals("COMPLETED", status("disappearing-claim", 1001L, ACTION_CODE));
    }

    @Test
    void sameScopeAndKeyWithDifferentParamsShouldConflict() {
        AtomicInteger executions = new AtomicInteger();
        PluginActionHandler handler = handler(params -> {
            executions.incrementAndGet();
            return ServiceResult.ok(params);
        });

        service.execute(PLUGIN_ID, ACTION_CODE, "conflict-key", ctx, Map.of("value", "a"), handler);

        assertThrows(PluginActionIdempotencyService.IdempotencyConflictException.class,
            () -> service.execute(
                PLUGIN_ID, ACTION_CODE, "conflict-key", ctx, Map.of("value", "b"), handler));
        assertEquals(1, executions.get());
    }

    @Test
    void callerActionAndKeyCaseShouldHaveIndependentScopes() {
        AtomicInteger executions = new AtomicInteger();
        PluginActionHandler handler = handler(params -> {
            executions.incrementAndGet();
            return ServiceResult.ok(Map.of("count", executions.get()));
        });
        InvocationContext otherUser = binder.forHttpRequest(
            PLUGIN_ID,
            1002L,
            InvocationContext.CallerType.ADMIN,
            "request-2"
        );
        InvocationContext employeeCaller = binder.forHttpRequest(
            PLUGIN_ID,
            1001L,
            InvocationContext.CallerType.USER,
            "request-3"
        );
        String otherPluginId = PLUGIN_ID + "-other";
        InvocationContext otherPlugin = binder.forHttpRequest(
            otherPluginId,
            1001L,
            InvocationContext.CallerType.ADMIN,
            "request-4"
        );

        service.execute(PLUGIN_ID, ACTION_CODE, "Case-Key", ctx, Map.of(), handler);
        service.execute(PLUGIN_ID, ACTION_CODE, "case-key", ctx, Map.of(), handler);
        service.execute(PLUGIN_ID, ACTION_CODE, "Case-Key", otherUser, Map.of(), handler);
        service.execute(PLUGIN_ID, ACTION_CODE, "Case-Key", employeeCaller, Map.of(), handler);
        service.execute(PLUGIN_ID, "action.idempotency.other", "Case-Key", ctx, Map.of(), handler);
        service.execute(otherPluginId, ACTION_CODE, "Case-Key", otherPlugin, Map.of(), handler);

        assertEquals(6, executions.get());
        assertEquals(5, rowCount());
        assertEquals(1, rowCount(otherPluginId));
    }

    @Test
    void simultaneousSameRequestShouldExecuteOnlyOnce() throws Exception {
        CountDownLatch insertsEntered = synchronizeClaimInserts("race-same");
        CountDownLatch duplicateObserved = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        PluginActionHandler handler = handler(params -> {
            executions.incrementAndGet();
            try {
                assertTrue(duplicateObserved.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return ServiceResult.ok(Map.of("value", "done"));
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> executeRace(
                "race-same", Map.of("value", "same"), handler, duplicateObserved));
            Future<String> second = executor.submit(() -> executeRace(
                "race-same", Map.of("value", "same"), handler, duplicateObserved));
            assertTrue(insertsEntered.await(5, TimeUnit.SECONDS));

            assertEquals(java.util.Set.of("SUCCESS", "IN_PROGRESS"),
                java.util.Set.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)));
            assertEquals(1, executions.get());
            assertEquals(1, rowCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void simultaneousDifferentRequestShouldExecuteOnlyWinner() throws Exception {
        CountDownLatch insertsEntered = synchronizeClaimInserts("race-different");
        CountDownLatch duplicateObserved = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        PluginActionHandler handler = handler(params -> {
            executions.incrementAndGet();
            try {
                assertTrue(duplicateObserved.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return ServiceResult.ok(params);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> executeRace(
                "race-different", Map.of("value", "a"), handler, duplicateObserved));
            Future<String> second = executor.submit(() -> executeRace(
                "race-different", Map.of("value", "b"), handler, duplicateObserved));
            assertTrue(insertsEntered.await(5, TimeUnit.SECONDS));

            assertEquals(java.util.Set.of("SUCCESS", "CONFLICT"),
                java.util.Set.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)));
            assertEquals(1, executions.get());
            assertEquals(1, rowCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void activeClaimShouldReturnRetryableConflictWithoutSecondExecution() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        PluginActionHandler handler = handler(params -> {
            executions.incrementAndGet();
            started.countDown();
            try {
                assertTrue(finish.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return ServiceResult.ok(Map.of("value", "done"));
        });

        CompletableFuture<ServiceResult<Object>> first = CompletableFuture.supplyAsync(() ->
            service.execute(PLUGIN_ID, ACTION_CODE, "active-key", ctx, Map.of(), handler));
        assertTrue(started.await(5, TimeUnit.SECONDS));

        assertThrows(PluginActionIdempotencyService.ActionInProgressException.class,
            () -> service.execute(PLUGIN_ID, ACTION_CODE, "active-key", ctx, Map.of(), handler));
        assertEquals(1, executions.get());

        finish.countDown();
        assertEquals(0, first.get(5, TimeUnit.SECONDS).getCode());
    }

    @Test
    void handlerExceptionShouldReleaseClaimForRetry() {
        AtomicInteger executions = new AtomicInteger();
        PluginActionHandler handler = handler(params -> {
            if (executions.incrementAndGet() == 1) {
                throw new IllegalStateException("handler failure");
            }
            return ServiceResult.ok(Map.of("value", "retried"));
        });

        assertThrows(IllegalStateException.class,
            () -> service.execute(PLUGIN_ID, ACTION_CODE, "handler-failure", ctx, Map.of(), handler));
        assertEquals(0, rowCount());

        assertEquals(0,
            service.execute(PLUGIN_ID, ACTION_CODE, "handler-failure", ctx, Map.of(), handler).getCode());
        assertEquals(2, executions.get());
    }

    @Test
    void retryableResultShouldReleaseClaimForRetry() {
        AtomicInteger executions = new AtomicInteger();
        PluginActionHandler handler = handler(params -> executions.incrementAndGet() == 1
            ? ServiceResult.fail(4001, "retry", true)
            : ServiceResult.ok(Map.of("value", "retried")));

        ServiceResult<Object> retryable = service.execute(
            PLUGIN_ID, ACTION_CODE, "retryable-key", ctx, Map.of(), handler);
        assertTrue(retryable.isRetryable());
        assertEquals(0, rowCount());

        assertEquals(0,
            service.execute(PLUGIN_ID, ACTION_CODE, "retryable-key", ctx, Map.of(), handler).getCode());
        assertEquals(2, executions.get());
    }

    @Test
    void nonRetryableFailureShouldBeReplayed() {
        AtomicInteger executions = new AtomicInteger();
        PluginActionHandler handler = handler(params -> {
            executions.incrementAndGet();
            return ServiceResult.fail(3000, "failed", false);
        });

        ServiceResult<Object> initial = service.execute(
            PLUGIN_ID, ACTION_CODE, "failure-key", ctx, Map.of(), handler);
        ServiceResult<Object> replay = service.execute(
            PLUGIN_ID, ACTION_CODE, "failure-key", ctx, Map.of(), handler);

        assertEquals(3000, initial.getCode());
        assertEquals(initial.getMessage(), replay.getMessage());
        assertEquals(1, executions.get());
    }

    @Test
    void serializationFailureShouldLeaveFailClosedClaim() {
        AtomicInteger executions = new AtomicInteger();
        PluginActionHandler handler = handler(params -> {
            executions.incrementAndGet();
            return ServiceResult.ok(new BrokenResponse());
        });

        assertThrows(IllegalStateException.class,
            () -> service.execute(PLUGIN_ID, ACTION_CODE, "serialization-key", ctx, Map.of(), handler));
        assertEquals("IN_PROGRESS", status("serialization-key", 1001L, ACTION_CODE));
        assertThrows(PluginActionIdempotencyService.ActionInProgressException.class,
            () -> service.execute(PLUGIN_ID, ACTION_CODE, "serialization-key", ctx, Map.of(), handler));
        assertEquals(1, executions.get());

        jdbc.update("""
            UPDATE sys_plugin_action_idempotency
            SET created_at = DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 25 HOUR)
            WHERE plugin_id = ? AND idempotency_key = ?
            """, PLUGIN_ID, "serialization-key");
        cleanupJob.cleanupExpired();

        assertEquals("IN_PROGRESS", status("serialization-key", 1001L, ACTION_CODE));
        assertThrows(PluginActionIdempotencyService.ActionInProgressException.class,
            () -> service.execute(PLUGIN_ID, ACTION_CODE, "serialization-key", ctx, Map.of(), handler));
        assertEquals(1, executions.get());
    }

    @Test
    void oversizedResponseShouldLeaveFailClosedClaim() {
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger serializedValues = new AtomicInteger();
        PluginActionHandler handler = handler(params -> {
            executions.incrementAndGet();
            return ServiceResult.ok(new RepeatingValues(serializedValues, 1_000_000));
        });

        assertThrows(IllegalStateException.class,
            () -> service.execute(PLUGIN_ID, ACTION_CODE, "large-response", ctx, Map.of(), handler));
        assertEquals("IN_PROGRESS", status("large-response", 1001L, ACTION_CODE));
        assertThrows(PluginActionIdempotencyService.ActionInProgressException.class,
            () -> service.execute(PLUGIN_ID, ACTION_CODE, "large-response", ctx, Map.of(), handler));
        assertEquals(1, executions.get());
        assertTrue(serializedValues.get() < 1_000_000);
    }

    @Test
    void completionWriteFailureShouldLeaveFailClosedClaim() {
        AtomicInteger executions = new AtomicInteger();
        PluginActionHandler handler = handler(params -> {
            executions.incrementAndGet();
            return ServiceResult.ok(Map.of("value", "done"));
        });
        doThrow(new DataAccessResourceFailureException("forced complete failure"))
            .when(repository).complete(any(), anyString(), anyString(), anyString(), anyLong());

        assertThrows(DataAccessResourceFailureException.class,
            () -> service.execute(PLUGIN_ID, ACTION_CODE, "complete-failure", ctx, Map.of(), handler));
        assertEquals("IN_PROGRESS", status("complete-failure", 1001L, ACTION_CODE));
        assertThrows(PluginActionIdempotencyService.ActionInProgressException.class,
            () -> service.execute(PLUGIN_ID, ACTION_CODE, "complete-failure", ctx, Map.of(), handler));
        assertEquals(1, executions.get());
    }

    @Test
    void expiredCompletedResponseShouldAllowKeyReuse() {
        AtomicInteger executions = new AtomicInteger();
        PluginActionHandler handler = handler(params -> {
            executions.incrementAndGet();
            return ServiceResult.ok(params);
        });

        service.execute(
            PLUGIN_ID, ACTION_CODE, "expired-completed", ctx, Map.of("value", "old"), handler);
        jdbc.update("""
            UPDATE sys_plugin_action_idempotency
            SET expires_at = DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 SECOND)
            WHERE plugin_id = ? AND idempotency_key = ?
            """, PLUGIN_ID, "expired-completed");

        ServiceResult<Object> current = service.execute(
            PLUGIN_ID, ACTION_CODE, "expired-completed", ctx, Map.of("value", "new"), handler);
        assertEquals(Map.of("value", "new"), current.getData());
        assertEquals(2, executions.get());
    }

    @Test
    void corruptedCompletedResponseShouldFailClosed() {
        AtomicInteger executions = new AtomicInteger();
        PluginActionHandler handler = handler(params -> {
            executions.incrementAndGet();
            return ServiceResult.ok(Map.of("value", "done"));
        });
        service.execute(PLUGIN_ID, ACTION_CODE, "corrupt-response", ctx, Map.of(), handler);
        jdbc.update("""
            UPDATE sys_plugin_action_idempotency
            SET response_json = CAST('{}' AS JSON)
            WHERE plugin_id = ? AND idempotency_key = ?
            """, PLUGIN_ID, "corrupt-response");

        assertThrows(IllegalStateException.class,
            () -> service.execute(PLUGIN_ID, ACTION_CODE, "corrupt-response", ctx, Map.of(), handler));
        assertEquals(1, executions.get());
    }

    @Test
    void cleanupShouldDeleteOnlyExpiredCompleted() {
        insertCleanupRow("expired-completed", "COMPLETED", -1, -1);
        insertCleanupRow("active-completed", "COMPLETED", -1, 1);
        insertCleanupRow("stale-claim", "IN_PROGRESS", -25, 1);
        insertCleanupRow("active-claim", "IN_PROGRESS", 0, 1);

        cleanupJob.cleanupExpired();

        assertEquals(
            java.util.Set.of("active-completed", "stale-claim", "active-claim"),
            new java.util.HashSet<>(jdbc.queryForList(
                "SELECT idempotency_key FROM sys_plugin_action_idempotency WHERE plugin_id = ?",
                String.class,
                PLUGIN_ID
            ))
        );
    }

    @Test
    void cleanupShouldDrainMoreThanOneBatch() {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            rows.add(new Object[]{PLUGIN_ID, ACTION_CODE, "expired-batch-" + i, "a".repeat(64)});
        }
        jdbc.batchUpdate("""
            INSERT INTO sys_plugin_action_idempotency
                (plugin_id, action_code, caller_type, caller_id, idempotency_key,
                 request_hash, status, claim_token, response_json, created_at, expires_at)
            VALUES (?, ?, 'ADMIN', 1001, ?, ?, 'COMPLETED', NULL,
                    CAST('{"code":0,"message":"success","data":null,"retryable":false}' AS JSON),
                    DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 HOUR),
                    DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 SECOND))
            """, rows);

        cleanupJob.cleanupExpired();

        assertEquals(0, rowCount());
    }

    private PluginActionHandler handler(ActionExecution execution) {
        PluginActionHandler handler = mock(PluginActionHandler.class);
        when(handler.getDescriptor()).thenReturn(new ActionDescriptor(
            ACTION_CODE,
            "test",
            "test",
            Map.of(),
            Map.of(),
            "TEST",
            Duration.ofSeconds(5),
            true
        ));
        when(handler.execute(any(InvocationContext.class), any())).thenAnswer(invocation ->
            execution.execute(invocation.getArgument(1)));
        return handler;
    }

    private int rowCount() {
        return rowCount(PLUGIN_ID);
    }

    private int rowCount(String pluginId) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_action_idempotency WHERE plugin_id = ?",
            Integer.class,
            pluginId
        );
        return count == null ? 0 : count;
    }

    private String status(String key, Long callerId, String actionCode) {
        return jdbc.queryForObject("""
            SELECT status
            FROM sys_plugin_action_idempotency
            WHERE plugin_id = ?
              AND action_code = ?
              AND caller_type = 'ADMIN'
              AND caller_id = ?
              AND idempotency_key = ?
            """,
            String.class,
            PLUGIN_ID,
            actionCode,
            callerId,
            key
        );
    }

    private void cleanupRows() {
        jdbc.update("DELETE FROM sys_plugin_action_idempotency WHERE plugin_id LIKE ?", PLUGIN_ID + "%");
    }

    private CountDownLatch synchronizeClaimInserts(String key) {
        CountDownLatch entered = new CountDownLatch(2);
        doAnswer(invocation -> {
            entered.countDown();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            return invocation.callRealMethod();
        }).when(repository).insertClaim(
            eq(PLUGIN_ID),
            eq(ACTION_CODE),
            eq("ADMIN"),
            eq(1001L),
            eq(key),
            anyString(),
            anyString()
        );
        return entered;
    }

    private String executeRace(
            String key,
            Map<String, Object> params,
            PluginActionHandler handler,
            CountDownLatch duplicateObserved) {
        try {
            service.execute(PLUGIN_ID, ACTION_CODE, key, ctx, params, handler);
            return "SUCCESS";
        } catch (PluginActionIdempotencyService.ActionInProgressException e) {
            duplicateObserved.countDown();
            return "IN_PROGRESS";
        } catch (PluginActionIdempotencyService.IdempotencyConflictException e) {
            duplicateObserved.countDown();
            return "CONFLICT";
        }
    }

    private void insertCleanupRow(String key, String status, int createdHours, int expiresHours) {
        jdbc.update("""
            INSERT INTO sys_plugin_action_idempotency
                (plugin_id, action_code, caller_type, caller_id, idempotency_key,
                 request_hash, status, claim_token, response_json, created_at, expires_at)
            VALUES (?, ?, 'ADMIN', 1001, ?, ?, ?, ?, CAST(? AS JSON),
                    DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL ? HOUR),
                    CASE WHEN ? = 'COMPLETED'
                        THEN DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL ? HOUR)
                        ELSE NULL
                    END)
            """,
            PLUGIN_ID,
            ACTION_CODE,
            key,
            "a".repeat(64),
            status,
            "IN_PROGRESS".equals(status) ? java.util.UUID.randomUUID().toString() : null,
            "COMPLETED".equals(status)
                ? "{\"code\":0,\"message\":\"success\",\"data\":null,\"retryable\":false}"
                : null,
            createdHours,
            status,
            expiresHours
        );
    }

    @FunctionalInterface
    private interface ActionExecution {
        ServiceResult<Object> execute(Map<String, Object> params);
    }

    private static final class BrokenResponse {

        public String getValue() {
            throw new IllegalStateException("broken response");
        }
    }

    private static final class RepeatingValues implements Iterable<String> {

        private final AtomicInteger serializedValues;
        private final int total;

        private RepeatingValues(AtomicInteger serializedValues, int total) {
            this.serializedValues = serializedValues;
            this.total = total;
        }

        @Override
        public Iterator<String> iterator() {
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return serializedValues.get() < total;
                }

                @Override
                public String next() {
                    serializedValues.incrementAndGet();
                    return "x".repeat(128);
                }
            };
        }
    }
}
