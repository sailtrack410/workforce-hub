package com.workforcehub.plugin.sdk;

import org.junit.jupiter.api.Test;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkforcePluginLifecycleTest {

    @Test
    void shouldCloseStartResourcesInReverseOrderWhenStartFails() {
        ProbePlugin plugin = createPlugin();
        RuntimeException startFailure = new RuntimeException("start failed");
        plugin.addCleanup("first", null);
        plugin.addCleanup("second", null);
        plugin.startFailure = startFailure;

        PluginRuntimeException thrown = assertThrows(PluginRuntimeException.class, plugin::start);

        assertSame(startFailure, thrown.getCause());
        assertEquals(List.of("second", "first"), plugin.closed);
    }

    @Test
    void shouldRunEveryCleanupAndPreserveStopFailure() {
        ProbePlugin plugin = createPlugin();
        RuntimeException stopFailure = new RuntimeException("stop failed");
        Exception firstFailure = new Exception("first close failed");
        Exception thirdFailure = new Exception("third close failed");
        plugin.addCleanup("first", firstFailure);
        plugin.addCleanup("second", null);
        plugin.addCleanup("third", thirdFailure);
        plugin.stopFailure = stopFailure;
        plugin.start();

        PluginRuntimeException thrown = assertThrows(PluginRuntimeException.class, plugin::stop);

        assertSame(stopFailure, thrown.getCause());
        assertEquals(List.of("third", "second", "first"), plugin.closed);
        assertEquals(List.of(thirdFailure, firstFailure), List.of(stopFailure.getSuppressed()));
    }

    @Test
    void shouldUseFirstCleanupFailureAsLifecycleCause() {
        ProbePlugin plugin = createPlugin();
        Exception firstFailure = new Exception("first close failed");
        Exception secondFailure = new Exception("second close failed");
        plugin.addCleanup("first", firstFailure);
        plugin.addCleanup("second", secondFailure);
        plugin.start();

        PluginRuntimeException thrown = assertThrows(PluginRuntimeException.class, plugin::stop);

        assertSame(secondFailure, thrown.getCause());
        assertEquals(List.of(firstFailure), List.of(secondFailure.getSuppressed()));
    }

    @Test
    void shouldRetainFailedCleanupForRetryWithoutRepeatingSuccessfulStopHook() {
        RetryPlugin plugin = new RetryPlugin(mock(PluginWrapper.class), mock(PluginContext.class));
        plugin.start();

        assertThrows(PluginRuntimeException.class, plugin::stop);
        assertTrue(plugin.hasPendingLifecycleWork());
        assertEquals(1, plugin.stopCalls.get());
        assertEquals(1, plugin.cleanupCalls.get());

        assertDoesNotThrow(plugin::stop);
        assertFalse(plugin.hasPendingLifecycleWork());
        assertEquals(1, plugin.stopCalls.get());
        assertEquals(2, plugin.cleanupCalls.get());
    }

    @Test
    void shouldRetainCorrectEntryWhenSameCleanupInstanceIsRegisteredTwice() {
        DuplicateCleanupPlugin plugin = new DuplicateCleanupPlugin(
            mock(PluginWrapper.class), mock(PluginContext.class));
        plugin.start();

        assertThrows(PluginRuntimeException.class, plugin::stop);
        assertTrue(plugin.hasPendingLifecycleWork());
        assertEquals(2, plugin.cleanupCalls.get());

        assertDoesNotThrow(plugin::stop);
        assertFalse(plugin.hasPendingLifecycleWork());
        assertEquals(3, plugin.cleanupCalls.get());
    }

    @Test
    void shouldRejectRestartUntilFailedStartCleanupSucceeds() {
        StartFailurePlugin plugin = new StartFailurePlugin(
            mock(PluginWrapper.class), mock(PluginContext.class));

        assertThrows(PluginRuntimeException.class, plugin::start);
        assertTrue(plugin.hasPendingLifecycleWork());
        assertEquals(1, plugin.startCalls.get());

        assertThrows(PluginRuntimeException.class, plugin::start);
        assertEquals(1, plugin.startCalls.get());

        assertDoesNotThrow(plugin::stop);
        assertFalse(plugin.hasPendingLifecycleWork());
        assertDoesNotThrow(plugin::start);
        assertEquals(2, plugin.startCalls.get());
        assertDoesNotThrow(plugin::stop);
    }

    @Test
    void shouldContinueCleanupBeforeRestoringInterruptStatus() {
        InterruptPlugin plugin = new InterruptPlugin(
            mock(PluginWrapper.class), mock(PluginContext.class));
        plugin.start();

        try {
            PluginRuntimeException thrown = assertThrows(PluginRuntimeException.class, plugin::stop);

            assertTrue(thrown.getCause() instanceof InterruptedException);
            assertTrue(plugin.cleanupAfterInterrupt.get());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldIgnoreSelfSuppressionAndContinueRemainingCleanups() {
        ProbePlugin plugin = createPlugin();
        RuntimeException sharedFailure = new RuntimeException("shared failure");
        plugin.addCleanup("after", null);
        plugin.addCleanup("shared", sharedFailure);
        plugin.stopFailure = sharedFailure;
        plugin.start();

        PluginRuntimeException thrown = assertThrows(PluginRuntimeException.class, plugin::stop);

        assertSame(sharedFailure, thrown.getCause());
        assertEquals(0, sharedFailure.getSuppressed().length);
        assertEquals(List.of("shared", "after"), plugin.closed);
    }

    @Test
    void shouldWrapNonFatalCleanupError() {
        ProbePlugin plugin = createPlugin();
        AssertionError cleanupError = new AssertionError("cleanup failed");
        plugin.addCleanup("error", cleanupError);
        plugin.start();

        PluginRuntimeException thrown = assertThrows(PluginRuntimeException.class, plugin::stop);

        assertSame(cleanupError, thrown.getCause());
        assertTrue(plugin.hasPendingLifecycleWork());
    }

    @Test
    void shouldRejectConcurrentStart() throws Exception {
        BlockingStartPlugin plugin = new BlockingStartPlugin(
            mock(PluginWrapper.class), mock(PluginContext.class));
        CompletableFuture<Void> firstStart = CompletableFuture.runAsync(plugin::start);
        assertTrue(plugin.startEntered.await(2, TimeUnit.SECONDS));

        try {
            assertThrows(PluginRuntimeException.class, plugin::start);
        } finally {
            plugin.startRelease.countDown();
        }
        firstStart.get(5, TimeUnit.SECONDS);
        assertDoesNotThrow(plugin::stop);
    }

    @Test
    void shouldRejectConcurrentStop() throws Exception {
        BlockingStopPlugin plugin = new BlockingStopPlugin(
            mock(PluginWrapper.class), mock(PluginContext.class));
        plugin.start();
        CompletableFuture<Void> firstStop = CompletableFuture.runAsync(plugin::stop);
        assertTrue(plugin.stopEntered.await(2, TimeUnit.SECONDS));

        try {
            assertThrows(PluginRuntimeException.class, plugin::stop);
        } finally {
            plugin.stopRelease.countDown();
        }
        firstStop.get(5, TimeUnit.SECONDS);
        assertFalse(plugin.hasPendingLifecycleWork());
    }

    @Test
    void shouldRejectReentrantStopDuringStart() {
        ReentrantStopPlugin plugin = new ReentrantStopPlugin(
            mock(PluginWrapper.class), mock(PluginContext.class));

        assertDoesNotThrow(plugin::start);
        assertTrue(plugin.reentrantFailure instanceof PluginRuntimeException);
        assertDoesNotThrow(plugin::stop);
    }

    @Test
    void shouldCloseCleanupRegisteredAfterStopImmediately() {
        ProbePlugin plugin = createPlugin();
        AtomicBoolean closed = new AtomicBoolean();
        plugin.start();
        plugin.stop();

        assertThrows(
            IllegalStateException.class,
            () -> plugin.registerLateCleanup(() -> closed.set(true))
        );
        assertTrue(closed.get());
    }

    @Test
    void shouldCloseCleanupRegisteredAfterStartImmediately() {
        ProbePlugin plugin = createPlugin();
        AtomicBoolean closed = new AtomicBoolean();
        plugin.start();

        try {
            assertThrows(
                IllegalStateException.class,
                () -> plugin.registerLateCleanup(() -> closed.set(true))
            );
            assertTrue(closed.get());
        } finally {
            plugin.stop();
        }
    }

    private ProbePlugin createPlugin() {
        PluginWrapper wrapper = mock(PluginWrapper.class);
        when(wrapper.getPluginId()).thenReturn("probe-plugin");
        return new ProbePlugin(wrapper, mock(PluginContext.class));
    }

    private static final class ProbePlugin extends WorkforcePlugin {

        private final List<CleanupSpec> cleanups = new ArrayList<>();
        private final List<String> closed = new ArrayList<>();
        private RuntimeException startFailure;
        private RuntimeException stopFailure;

        private ProbePlugin(PluginWrapper wrapper, PluginContext context) {
            super(wrapper, context);
        }

        private void addCleanup(String name, Throwable failure) {
            cleanups.add(new CleanupSpec(name, failure));
        }

        private void registerLateCleanup(AutoCloseable cleanup) {
            registerCleanup(cleanup);
        }

        @Override
        protected void onStart() {
            for (CleanupSpec cleanup : cleanups) {
                registerCleanup(() -> {
                    closed.add(cleanup.name());
                    if (cleanup.failure() instanceof Exception exception) {
                        throw exception;
                    }
                    if (cleanup.failure() instanceof Error error) {
                        throw error;
                    }
                });
            }
            if (startFailure != null) {
                throw startFailure;
            }
        }

        @Override
        protected void onStop() {
            if (stopFailure != null) {
                throw stopFailure;
            }
        }
    }

    private static final class RetryPlugin extends WorkforcePlugin {

        private final AtomicInteger stopCalls = new AtomicInteger();
        private final AtomicInteger cleanupCalls = new AtomicInteger();

        private RetryPlugin(PluginWrapper wrapper, PluginContext context) {
            super(wrapper, context);
        }

        @Override
        protected void onStart() {
            registerCleanup(() -> {
                if (cleanupCalls.incrementAndGet() == 1) {
                    throw new IllegalStateException("retry cleanup");
                }
            });
        }

        @Override
        protected void onStop() {
            stopCalls.incrementAndGet();
        }
    }

    private static final class StartFailurePlugin extends WorkforcePlugin {

        private final AtomicInteger startCalls = new AtomicInteger();
        private final AtomicInteger cleanupCalls = new AtomicInteger();
        private boolean failStart = true;

        private StartFailurePlugin(PluginWrapper wrapper, PluginContext context) {
            super(wrapper, context);
        }

        @Override
        protected void onStart() {
            startCalls.incrementAndGet();
            registerCleanup(() -> {
                if (cleanupCalls.incrementAndGet() == 1) {
                    throw new IllegalStateException("retry startup cleanup");
                }
            });
            if (failStart) {
                failStart = false;
                throw new IllegalStateException("start failed");
            }
        }
    }

    private static final class DuplicateCleanupPlugin extends WorkforcePlugin {

        private final AtomicInteger cleanupCalls = new AtomicInteger();
        private final AutoCloseable cleanup = () -> {
            if (cleanupCalls.incrementAndGet() == 1) {
                throw new IllegalStateException("first cleanup failed");
            }
        };

        private DuplicateCleanupPlugin(PluginWrapper wrapper, PluginContext context) {
            super(wrapper, context);
        }

        @Override
        protected void onStart() {
            registerCleanup(cleanup);
            registerCleanup(cleanup);
        }
    }

    private static final class InterruptPlugin extends WorkforcePlugin {

        private final AtomicBoolean cleanupAfterInterrupt = new AtomicBoolean();

        private InterruptPlugin(PluginWrapper wrapper, PluginContext context) {
            super(wrapper, context);
        }

        @Override
        protected void onStart() {
            registerCleanup(() -> cleanupAfterInterrupt.set(true));
            registerCleanup(() -> {
                throw new InterruptedException("cleanup interrupted");
            });
        }
    }

    private static final class BlockingStartPlugin extends WorkforcePlugin {

        private final CountDownLatch startEntered = new CountDownLatch(1);
        private final CountDownLatch startRelease = new CountDownLatch(1);

        private BlockingStartPlugin(PluginWrapper wrapper, PluginContext context) {
            super(wrapper, context);
        }

        @Override
        protected void onStart() throws InterruptedException {
            startEntered.countDown();
            if (!startRelease.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("start release timeout");
            }
        }
    }

    private static final class BlockingStopPlugin extends WorkforcePlugin {

        private final CountDownLatch stopEntered = new CountDownLatch(1);
        private final CountDownLatch stopRelease = new CountDownLatch(1);

        private BlockingStopPlugin(PluginWrapper wrapper, PluginContext context) {
            super(wrapper, context);
        }

        @Override
        protected void onStop() throws InterruptedException {
            stopEntered.countDown();
            if (!stopRelease.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("stop release timeout");
            }
        }
    }

    private static final class ReentrantStopPlugin extends WorkforcePlugin {

        private Throwable reentrantFailure;

        private ReentrantStopPlugin(PluginWrapper wrapper, PluginContext context) {
            super(wrapper, context);
        }

        @Override
        protected void onStart() {
            try {
                stop();
            } catch (Throwable failure) {
                reentrantFailure = failure;
            }
        }
    }

    private record CleanupSpec(String name, Throwable failure) {
    }
}
