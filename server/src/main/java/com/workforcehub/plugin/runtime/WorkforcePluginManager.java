package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.sdk.HostService;
import com.workforcehub.plugin.sdk.WorkforcePlugin;
import jakarta.annotation.PreDestroy;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginFactory;
import org.pf4j.PluginRuntimeException;
import org.pf4j.PluginState;
import org.pf4j.PluginStateEvent;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Workforce Hub 的 PF4J 管理器（Spring 单例）。
 * 全局只使用一个实例，由 Spring 注入。
 */
@Component
public final class WorkforcePluginManager extends DefaultPluginManager {

    private final HostServiceRegistry serviceRegistry;
    private final Map<String, WorkforcePlugin> pluginInstances = new ConcurrentHashMap<>();

    @Autowired
    public WorkforcePluginManager(
            @Value("${workforce.plugin.root:plugins}") String pluginsRoot,
            List<HostService> hostServices,
            PluginPermissionService permissionService) {
        super(Path.of(pluginsRoot));
        this.serviceRegistry = new HostServiceRegistry(hostServices, permissionService);
    }

    /**
     * 测试用构造器，无 HostService 注入。
     * permissionService 必须显式传入，fail closed。
     */
    public WorkforcePluginManager(String pluginsRoot, PluginPermissionService permissionService) {
        this(pluginsRoot, List.of(), permissionService);
    }

    @Override
    protected PluginFactory createPluginFactory() {
        return new WorkforcePluginFactory(
            () -> serviceRegistry,
            (pluginId, plugin) -> pluginInstances.put(pluginId, plugin)
        );
    }

    @Override
    public synchronized String loadPlugin(Path pluginPath) {
        return super.loadPlugin(pluginPath);
    }

    @Override
    public synchronized void loadPlugins() {
        super.loadPlugins();
    }

    @Override
    public synchronized PluginState startPlugin(String pluginId) {
        return super.startPlugin(pluginId);
    }

    @Override
    public synchronized void startPlugins() {
        super.startPlugins();
    }

    @Override
    public synchronized boolean unloadPlugin(String pluginId) {
        List<PluginWrapper> pluginsToUnload = pluginsToUnload(pluginId);
        if (pluginsToUnload.isEmpty()) {
            return false;
        }

        boolean ready = true;
        for (PluginWrapper wrapper : pluginsToUnload) {
            if (!prepareForUnload(wrapper)) {
                ready = false;
            }
        }
        if (!ready) {
            return false;
        }

        try {
            return super.unloadPlugin(pluginId);
        } finally {
            removeUnloadedInstances(pluginsToUnload);
        }
    }

    @Override
    public synchronized PluginState stopPlugin(String pluginId) {
        PluginWrapper wrapper = getPlugin(pluginId);
        if (wrapper != null && wrapper.getPluginState() == PluginState.FAILED) {
            return retryFailedLifecycle(wrapper) ? PluginState.STOPPED : PluginState.FAILED;
        }
        return super.stopPlugin(pluginId);
    }

    synchronized boolean unloadPluginWithoutDependents(String pluginId) {
        PluginWrapper wrapper = getPlugin(pluginId);
        if (wrapper == null || !prepareForUnload(wrapper)) {
            return false;
        }
        try {
            return super.unloadPlugin(pluginId, false, false);
        } finally {
            if (getPlugin(pluginId) == null) {
                pluginInstances.remove(pluginId);
            }
        }
    }

    @Override
    public synchronized void stopPlugins() {
        List<PluginWrapper> started = new ArrayList<>(startedPlugins);
        for (PluginWrapper wrapper : getPlugins()) {
            if (wrapper.getPluginState() == PluginState.FAILED && !started.contains(wrapper)) {
                started.add(wrapper);
            }
        }
        Throwable failure = null;
        for (int index = started.size() - 1; index >= 0; index--) {
            PluginWrapper wrapper = started.get(index);
            try {
                if (!prepareForUnload(wrapper)) {
                    failure = mergeFailure(failure, pluginFailure(wrapper, "停止"));
                }
            } catch (Throwable stopError) {
                failure = mergeFailure(failure, stopError);
            }
        }
        throwIfFailed(failure);
    }

    @Override
    public synchronized void unloadPlugins() {
        close();
    }

    @PreDestroy
    public synchronized void close() {
        Throwable failure = null;
        for (PluginWrapper wrapper : new ArrayList<>(getPlugins())) {
            String pluginId = wrapper.getPluginId();
            if (getPlugin(pluginId) == null) {
                continue;
            }
            try {
                if (!unloadPlugin(pluginId)) {
                    PluginWrapper remaining = getPlugin(pluginId);
                    failure = mergeFailure(
                        failure,
                        pluginFailure(remaining == null ? wrapper : remaining, "关闭")
                    );
                }
            } catch (Throwable closeError) {
                failure = mergeFailure(failure, closeError);
            }
        }
        throwIfFailed(failure);
    }

    private List<PluginWrapper> pluginsToUnload(String pluginId) {
        List<PluginWrapper> ordered = new ArrayList<>();
        collectDependents(pluginId, new HashSet<>(), ordered);
        return ordered;
    }

    private void collectDependents(
            String pluginId,
            Set<String> visited,
            List<PluginWrapper> ordered) {
        if (!visited.add(pluginId)) {
            return;
        }
        for (String dependentId : new ArrayList<>(dependencyResolver.getDependents(pluginId))) {
            collectDependents(dependentId, visited, ordered);
        }
        PluginWrapper wrapper = getPlugin(pluginId);
        if (wrapper != null) {
            ordered.add(wrapper);
        }
    }

    private boolean prepareForUnload(PluginWrapper wrapper) {
        PluginState state = wrapper.getPluginState();
        if (state == PluginState.STARTED) {
            if (super.stopPlugin(wrapper.getPluginId(), false) != PluginState.STOPPED) {
                return false;
            }
        } else if (state == PluginState.FAILED) {
            if (!retryFailedLifecycle(wrapper)) {
                return false;
            }
        }

        WorkforcePlugin plugin = pluginInstances.get(wrapper.getPluginId());
        return wrapper.getPluginState() != PluginState.STARTED
            && !startedPlugins.contains(wrapper)
            && (plugin == null || !plugin.hasPendingLifecycleWork());
    }

    private boolean retryFailedLifecycle(PluginWrapper wrapper) {
        WorkforcePlugin plugin = pluginInstances.get(wrapper.getPluginId());
        if (plugin == null) {
            markStopped(wrapper);
            return true;
        }
        if (plugin.hasPendingLifecycleWork()) {
            try {
                plugin.stop();
            } catch (PluginRuntimeException retryError) {
                wrapper.setFailedException(retryError);
                return false;
            }
        }
        if (plugin.hasPendingLifecycleWork()) {
            return false;
        }

        markStopped(wrapper);
        return true;
    }

    private void markStopped(PluginWrapper wrapper) {
        PluginState oldState = wrapper.getPluginState();
        wrapper.setPluginState(PluginState.STOPPED);
        wrapper.setFailedException(null);
        startedPlugins.remove(wrapper);
        firePluginStateEvent(new PluginStateEvent(this, wrapper, oldState));
    }

    private void removeUnloadedInstances(List<PluginWrapper> wrappers) {
        for (PluginWrapper wrapper : wrappers) {
            if (getPlugin(wrapper.getPluginId()) == null) {
                pluginInstances.remove(wrapper.getPluginId());
            }
        }
    }

    private PluginRuntimeException pluginFailure(PluginWrapper wrapper, String phase) {
        Throwable cause = wrapper.getFailedException();
        if (cause == null) {
            return new PluginRuntimeException("插件%s失败: %s", phase, wrapper.getPluginId());
        }
        return new PluginRuntimeException(
            cause,
            "插件%s失败: %s",
            phase,
            wrapper.getPluginId()
        );
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

    private void throwIfFailed(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        throw new PluginRuntimeException(failure, "插件管理器关闭失败");
    }
}
