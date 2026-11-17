package com.workforcehub.plugin.sdk;

import org.pf4j.Plugin;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginWrapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Workforce Hub 插件基类。
 */
public abstract class WorkforcePlugin extends Plugin {

    private final PluginContext context;
    private final Deque<CleanupEntry> cleanups = new ArrayDeque<>();
    private boolean acceptingCleanups;
    private boolean starting;
    private boolean stopping;
    private boolean started;
    private boolean stopHookCompleted = true;

    protected WorkforcePlugin(PluginWrapper wrapper, PluginContext context) {
        super(wrapper);
        this.context = context;
    }

    protected final PluginContext getContext() {
        return context;
    }

    @Override
    public final void start() {
        boolean interrupted = Thread.interrupted();
        synchronized (cleanups) {
            if (starting || stopping || started || !stopHookCompleted || !cleanups.isEmpty()) {
                restoreInterrupt(interrupted);
                throw new PluginRuntimeException(
                    "插件正在运行或生命周期清理未完成，禁止启动: %s",
                    context.getPluginId()
                );
            }
            starting = true;
            acceptingCleanups = true;
        }

        Throwable failure = null;
        try {
            onStart();
        } catch (Throwable startError) {
            interrupted |= startError instanceof InterruptedException;
            failure = startError;
        }
        interrupted |= Thread.interrupted();

        if (failure != null) {
            CleanupResult result = closeCleanups(failure, interrupted);
            failure = result.failure();
            interrupted = result.interrupted();
        }
        synchronized (cleanups) {
            starting = false;
            acceptingCleanups = false;
            if (failure == null) {
                started = true;
                stopHookCompleted = false;
            }
        }

        restoreInterrupt(interrupted);
        if (failure != null) {
            throwLifecycleFailure("启动", failure);
        }
    }

    @Override
    public final void stop() {
        boolean interrupted = Thread.interrupted();
        boolean runStopHook;
        synchronized (cleanups) {
            if (starting || stopping) {
                restoreInterrupt(interrupted);
                throw new PluginRuntimeException(
                    "插件生命周期操作正在执行，禁止停止: %s",
                    context.getPluginId()
                );
            }
            stopping = true;
            acceptingCleanups = false;
            runStopHook = started && !stopHookCompleted;
        }

        Throwable failure = null;
        if (runStopHook) {
            try {
                onStop();
                synchronized (cleanups) {
                    stopHookCompleted = true;
                }
            } catch (Throwable stopError) {
                interrupted |= stopError instanceof InterruptedException;
                failure = stopError;
            }
        }
        interrupted |= Thread.interrupted();

        CleanupResult result = closeCleanups(failure, interrupted);
        failure = result.failure();
        interrupted = result.interrupted();
        synchronized (cleanups) {
            stopping = false;
            if (failure == null && cleanups.isEmpty() && stopHookCompleted) {
                started = false;
            }
        }

        restoreInterrupt(interrupted);
        if (failure != null) {
            throwLifecycleFailure("停止", failure);
        }
    }

    protected void onStart() throws Exception {
    }

    protected void onStop() throws Exception {
    }

    /**
     * 注册插件在 onStart 中取得的长期资源。启动失败或停止时按注册逆序关闭。
     */
    protected final <T extends AutoCloseable> T registerCleanup(T cleanup) {
        Objects.requireNonNull(cleanup, "cleanup");

        synchronized (cleanups) {
            if (acceptingCleanups) {
                cleanups.addFirst(new CleanupEntry(cleanup));
                return cleanup;
            }
        }

        IllegalStateException rejected = new IllegalStateException("插件不在启动阶段，禁止注册长期资源");
        boolean interrupted = Thread.interrupted();
        Throwable closeFailure = null;
        try {
            cleanup.close();
        } catch (Throwable closeError) {
            interrupted |= closeError instanceof InterruptedException;
            closeFailure = closeError;
        } finally {
            interrupted |= Thread.interrupted();
            restoreInterrupt(interrupted);
        }
        if (closeFailure instanceof VirtualMachineError fatal) {
            throw fatal;
        }
        if (closeFailure instanceof ThreadDeath death) {
            throw death;
        }
        if (closeFailure != null) {
            rejected.addSuppressed(closeFailure);
        }
        throw rejected;
    }

    /**
     * 供宿主管理器判断当前实例是否仍持有未完成的停止或资源清理工作。
     */
    public final boolean hasPendingLifecycleWork() {
        synchronized (cleanups) {
            return starting
                || stopping
                || !cleanups.isEmpty()
                || (started && !stopHookCompleted);
        }
    }

    private CleanupResult closeCleanups(Throwable failure, boolean interrupted) {
        List<CleanupEntry> pending;
        synchronized (cleanups) {
            acceptingCleanups = false;
            pending = new ArrayList<>(cleanups);
        }

        for (CleanupEntry entry : pending) {
            try {
                entry.cleanup().close();
                synchronized (cleanups) {
                    cleanups.remove(entry);
                }
            } catch (Throwable closeError) {
                interrupted |= closeError instanceof InterruptedException;
                failure = mergeFailure(failure, closeError);
            } finally {
                interrupted |= Thread.interrupted();
            }
        }
        return new CleanupResult(failure, interrupted);
    }

    private Throwable mergeFailure(Throwable failure, Throwable next) {
        if (failure == null) {
            return next;
        }
        if ((next instanceof VirtualMachineError || next instanceof ThreadDeath)
                && !(failure instanceof VirtualMachineError)
                && !(failure instanceof ThreadDeath)) {
            if (next != failure) {
                next.addSuppressed(failure);
            }
            return next;
        }
        if (failure != next) {
            failure.addSuppressed(next);
        }
        return failure;
    }

    private void throwLifecycleFailure(String phase, Throwable failure) {
        if (failure instanceof VirtualMachineError fatal) {
            throw fatal;
        }
        if (failure instanceof ThreadDeath death) {
            throw death;
        }
        if (failure instanceof PluginRuntimeException pluginError) {
            throw pluginError;
        }
        throw new PluginRuntimeException(
            failure,
            "插件%s失败: %s",
            phase,
            context.getPluginId()
        );
    }

    private void restoreInterrupt(boolean interrupted) {
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class CleanupEntry {

        private final AutoCloseable cleanup;

        private CleanupEntry(AutoCloseable cleanup) {
            this.cleanup = cleanup;
        }

        private AutoCloseable cleanup() {
            return cleanup;
        }
    }

    private record CleanupResult(Throwable failure, boolean interrupted) {
    }
}
