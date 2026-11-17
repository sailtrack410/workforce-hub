package com.workforcehub.plugin.runtime;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 插件生命周期守卫测试。
 * 覆盖：并发 acquire/release、disable 排空、超时、竞态。
 */
class PluginLifecycleGuardTest {

    private static final long TEST_TIMEOUT_SECONDS = 1;

    @Test
    void shouldAcquireAndRelease() {
        PluginLifecycleGuard guard = new PluginLifecycleGuard(TEST_TIMEOUT_SECONDS);
        guard.enable("p1");
        assertTrue(guard.tryAcquire("p1"));
        assertEquals(1, guard.getActiveCount("p1"));
        guard.release("p1");
        assertEquals(0, guard.getActiveCount("p1"));
    }

    @Test
    void shouldRejectAfterDisable() {
        PluginLifecycleGuard guard = new PluginLifecycleGuard(TEST_TIMEOUT_SECONDS);
        guard.enable("p1");
        assertTrue(guard.tryAcquire("p1"));
        guard.release("p1");

        guard.beginDisable("p1");
        assertFalse(guard.tryAcquire("p1"), "关门后应拒绝");
    }

    @Test
    void shouldWaitForActiveRequestsToDrain() throws Exception {
        PluginLifecycleGuard guard = new PluginLifecycleGuard(TEST_TIMEOUT_SECONDS);
        guard.enable("p1");

        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch releaseSignal = new CountDownLatch(1);

        CompletableFuture<Boolean> drainResult = CompletableFuture.supplyAsync(() -> {
            assertTrue(guard.tryAcquire("p1"));
            acquired.countDown();
            try {
                assertTrue(releaseSignal.await(5, TimeUnit.SECONDS), "releaseSignal 应在 5 秒内到达");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            guard.release("p1");
            return true;
        });

        assertTrue(acquired.await(2, TimeUnit.SECONDS), "acquire 应在 2 秒内完成");
        assertEquals(1, guard.getActiveCount("p1"));

        CompletableFuture<Boolean> disableResult = CompletableFuture.supplyAsync(() -> {
            guard.beginDisable("p1");
            return guard.awaitDrain("p1");
        });

        releaseSignal.countDown();

        assertTrue(drainResult.get(5, TimeUnit.SECONDS));
        assertTrue(disableResult.get(5, TimeUnit.SECONDS));
        assertEquals(0, guard.getActiveCount("p1"));
    }

    @Test
    void shouldReturnFalseOnDrainTimeout() throws Exception {
        PluginLifecycleGuard guard = new PluginLifecycleGuard(TEST_TIMEOUT_SECONDS);
        guard.enable("p1");

        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch neverRelease = new CountDownLatch(1);

        CompletableFuture<Void> stuckRequest = CompletableFuture.runAsync(() -> {
            assertTrue(guard.tryAcquire("p1"));
            acquired.countDown();
            try {
                assertTrue(neverRelease.await(10, TimeUnit.SECONDS), "neverRelease 应在 10 秒内到达");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertTrue(acquired.await(2, TimeUnit.SECONDS), "acquire 应在 2 秒内完成");

        guard.beginDisable("p1");
        boolean drained = guard.awaitDrain("p1");
        assertFalse(drained, "应该超时");

        neverRelease.countDown();
        stuckRequest.get(5, TimeUnit.SECONDS);
        guard.release("p1");
    }

    @Test
    void shouldNotGoNegativeOnExtraRelease() {
        PluginLifecycleGuard guard = new PluginLifecycleGuard(TEST_TIMEOUT_SECONDS);
        guard.enable("p1");
        assertTrue(guard.tryAcquire("p1"));
        guard.release("p1");
        guard.release("p1");
        assertEquals(0, guard.getActiveCount("p1"));
    }

    @Test
    void shouldReopenAfterEnable() {
        PluginLifecycleGuard guard = new PluginLifecycleGuard(TEST_TIMEOUT_SECONDS);
        guard.enable("p1");
        guard.beginDisable("p1");
        assertFalse(guard.tryAcquire("p1"));

        guard.enable("p1");
        assertTrue(guard.tryAcquire("p1"));
        guard.release("p1");
    }

    @Test
    void shouldHandleConcurrentAcquireRelease() throws Exception {
        PluginLifecycleGuard guard = new PluginLifecycleGuard(TEST_TIMEOUT_SECONDS);
        guard.enable("p1");
        int threadCount = 10;
        int opsPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < opsPerThread; j++) {
                        if (guard.tryAcquire("p1")) {
                            successCount.incrementAndGet();
                            guard.release("p1");
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        assertEquals(threadCount * opsPerThread, successCount.get());
        assertEquals(0, guard.getActiveCount("p1"));
    }

    @Test
    void shouldProveDrainBlocksUntilRelease() throws Exception {
        PluginLifecycleGuard guard = new PluginLifecycleGuard(5);
        guard.enable("p1");

        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch drainStarted = new CountDownLatch(1);
        CountDownLatch drainReturned = new CountDownLatch(1);
        CountDownLatch releaseSignal = new CountDownLatch(1);

        CompletableFuture<Void> request = CompletableFuture.runAsync(() -> {
            assertTrue(guard.tryAcquire("p1"));
            acquired.countDown();
            try {
                assertTrue(releaseSignal.await(10, TimeUnit.SECONDS), "releaseSignal 应在 10 秒内到达");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            guard.release("p1");
        });

        assertTrue(acquired.await(2, TimeUnit.SECONDS), "acquire 应在 2 秒内完成");

        CompletableFuture<Boolean> disable = CompletableFuture.supplyAsync(() -> {
            guard.beginDisable("p1");
            drainStarted.countDown();
            boolean result = guard.awaitDrain("p1");
            drainReturned.countDown();
            return result;
        });

        assertTrue(drainStarted.await(2, TimeUnit.SECONDS), "drain 应在 2 秒内开始");
        assertFalse(drainReturned.await(500, TimeUnit.MILLISECONDS),
            "awaitDrain 应在 release 前阻塞");

        releaseSignal.countDown();
        assertTrue(disable.get(5, TimeUnit.SECONDS));
        assertTrue(drainReturned.await(2, TimeUnit.SECONDS), "drainReturned 应在 2 秒内到达");
        request.get(5, TimeUnit.SECONDS);
    }

    @Test
    void shouldNotCreateSlotForUnknownPlugin() {
        PluginLifecycleGuard guard = new PluginLifecycleGuard(TEST_TIMEOUT_SECONDS);
        assertFalse(guard.tryAcquire("unknown"));
        assertFalse(guard.hasSlot("unknown"), "未知插件不应创建 slot");
    }

    @Test
    void shouldNotReopenOnDrainTimeout() throws Exception {
        PluginLifecycleGuard guard = new PluginLifecycleGuard(TEST_TIMEOUT_SECONDS);
        guard.enable("p1");

        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch neverRelease = new CountDownLatch(1);

        CompletableFuture<Void> stuckRequest = CompletableFuture.runAsync(() -> {
            assertTrue(guard.tryAcquire("p1"));
            acquired.countDown();
            try {
                assertTrue(neverRelease.await(10, TimeUnit.SECONDS), "neverRelease 应在 10 秒内到达");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertTrue(acquired.await(2, TimeUnit.SECONDS), "acquire 应在 2 秒内完成");
        guard.beginDisable("p1");

        boolean drained = guard.awaitDrain("p1");
        assertFalse(drained);
        assertFalse(guard.tryAcquire("p1"), "超时后门应保持关闭");

        neverRelease.countDown();
        stuckRequest.get(5, TimeUnit.SECONDS);
        guard.release("p1");
    }

    @Test
    void shouldNeverAllowAcquireDuringOrAfterDisable() throws Exception {
        // 多轮竞态测试：acquire 和 disable 同时起跑，接受两种合法顺序
        PluginLifecycleGuard guard = new PluginLifecycleGuard(TEST_TIMEOUT_SECONDS);

        int rounds = 20;
        for (int r = 0; r < rounds; r++) {
            guard.enable("p1");

            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch acquireAttempted = new CountDownLatch(1);
            CountDownLatch releaseSignal = new CountDownLatch(1);
            AtomicInteger acquiredSucceeded = new AtomicInteger(0);
            AtomicInteger acquiredFailed = new AtomicInteger(0);

            // acquire 线程：同时起跑，记录成功或失败
            CompletableFuture<Void> acquireTask = CompletableFuture.runAsync(() -> {
                try {
                    assertTrue(startGate.await(2, TimeUnit.SECONDS), "startGate 应在 2 秒内打开");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                boolean acquired = guard.tryAcquire("p1");
                if (acquired) {
                    acquiredSucceeded.incrementAndGet();
                    acquireAttempted.countDown();
                    try {
                        assertTrue(releaseSignal.await(5, TimeUnit.SECONDS), "releaseSignal 应在 5 秒内打开");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    guard.release("p1");
                } else {
                    acquiredFailed.incrementAndGet();
                    acquireAttempted.countDown();
                }
            });

            // disable 线程：同时起跑
            CompletableFuture<Boolean> disableTask = CompletableFuture.supplyAsync(() -> {
                try {
                    assertTrue(startGate.await(2, TimeUnit.SECONDS), "startGate 应在 2 秒内打开");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                guard.beginDisable("p1");
                return guard.awaitDrain("p1");
            });

            // 同时起跑
            startGate.countDown();

            // 等待 acquire 尝试完成
            assertTrue(acquireAttempted.await(2, TimeUnit.SECONDS), "acquireAttempted 应在 2 秒内完成");

            if (acquiredSucceeded.get() == 1) {
                releaseSignal.countDown();
            }

            // 最终验证
            assertTrue(disableTask.get(5, TimeUnit.SECONDS),
                "第 " + r + " 轮：awaitDrain 应成功");
            acquireTask.get(5, TimeUnit.SECONDS);

            assertEquals(1, acquiredSucceeded.get() + acquiredFailed.get(),
                "第 " + r + " 轮：acquire 必须恰好成功或失败一次");
            assertEquals(0, guard.getActiveCount("p1"),
                "第 " + r + " 轮：activeCount 应为 0");
            assertFalse(guard.tryAcquire("p1"),
                "第 " + r + " 轮：disable 完成后必须拒绝");

            guard.remove("p1");
        }
    }
}
