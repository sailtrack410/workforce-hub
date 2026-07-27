package com.workforcehub.plugin.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 插件生命周期并发守卫。
 * 解决"状态检查后、计数前"竞态：tryAcquire 原子检查开门+计数。
 */
@Component
public class PluginLifecycleGuard {

    private static final Logger log = LoggerFactory.getLogger(PluginLifecycleGuard.class);
    static final long DEFAULT_DRAIN_TIMEOUT_SECONDS = 30;

    private final ConcurrentHashMap<String, PluginSlot> slots = new ConcurrentHashMap<>();
    private final long drainTimeoutSeconds;

    /**
     * 生产构造器，使用默认超时。
     */
    public PluginLifecycleGuard() {
        this(DEFAULT_DRAIN_TIMEOUT_SECONDS);
    }

    /**
     * 测试用构造器，允许自定义超时。
     */
    PluginLifecycleGuard(long drainTimeoutSeconds) {
        this.drainTimeoutSeconds = drainTimeoutSeconds;
    }

    /**
     * 请求入口：原子检查门开+计数。失败则插件正在停用或未注册。
     * 必须在 finally 中调用 release。
     * fail-closed：不创建 slot，未知插件直接拒绝。
     */
    public boolean tryAcquire(String pluginId) {
        PluginSlot slot = slots.get(pluginId);
        if (slot == null) {
            return false;
        }
        slot.lock.lock();
        try {
            if (!slot.gateOpen) {
                return false;
            }
            slot.activeCount++;
            return true;
        } finally {
            slot.lock.unlock();
        }
    }

    /**
     * 请求出口：finally 中调用。
     * 防止负数/错误释放：仅当 activeCount > 0 时才递减。
     */
    public void release(String pluginId) {
        PluginSlot slot = slots.get(pluginId);
        if (slot == null) {
            return;
        }
        slot.lock.lock();
        try {
            if (slot.activeCount > 0) {
                slot.activeCount--;
            }
            if (!slot.gateOpen && slot.activeCount == 0) {
                slot.drained.signalAll();
            }
        } finally {
            slot.lock.unlock();
        }
    }

    /**
     * 开始停用：关门，拒绝新请求。
     */
    public void beginDisable(String pluginId) {
        PluginSlot slot = slots.computeIfAbsent(pluginId, id -> new PluginSlot(false));
        slot.lock.lock();
        try {
            slot.gateOpen = false;
        } finally {
            slot.lock.unlock();
        }
    }

    /**
     * 等待在途请求排空。
     * 使用条件循环处理伪唤醒和剩余超时。
     * 返回 false 表示超时或中断。
     */
    public boolean awaitDrain(String pluginId) {
        PluginSlot slot = slots.get(pluginId);
        if (slot == null) {
            return true;
        }
        slot.lock.lock();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(drainTimeoutSeconds);
            while (slot.activeCount > 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    log.warn("插件 {} 排空超时，剩余活跃请求: {}", pluginId, slot.activeCount);
                    return false;
                }
                if (!slot.drained.await(remaining, TimeUnit.NANOSECONDS)) {
                    if (slot.activeCount > 0) {
                        log.warn("插件 {} 排空超时，剩余活跃请求: {}", pluginId, slot.activeCount);
                        return false;
                    }
                }
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("插件 {} 排空被中断", pluginId);
            return false;
        } finally {
            slot.lock.unlock();
        }
    }

    /**
     * 启用/恢复：创建并开门。
     */
    public void enable(String pluginId) {
        PluginSlot slot = slots.computeIfAbsent(pluginId, id -> new PluginSlot(true));
        slot.lock.lock();
        try {
            slot.gateOpen = true;
        } finally {
            slot.lock.unlock();
        }
    }

    /**
     * 卸载清理。
     */
    public void remove(String pluginId) {
        slots.remove(pluginId);
    }

    /**
     * 获取当前活跃请求数。
     */
    public int getActiveCount(String pluginId) {
        PluginSlot slot = slots.get(pluginId);
        if (slot == null) {
            return 0;
        }
        slot.lock.lock();
        try {
            return slot.activeCount;
        } finally {
            slot.lock.unlock();
        }
    }

    /**
     * 包可见：检查 slot 是否存在（测试用）。
     */
    boolean hasSlot(String pluginId) {
        return slots.containsKey(pluginId);
    }

    private static class PluginSlot {
        final ReentrantLock lock = new ReentrantLock();
        final Condition drained = lock.newCondition();
        int activeCount = 0;
        boolean gateOpen;

        PluginSlot(boolean gateOpen) {
            this.gateOpen = gateOpen;
        }
    }
}
