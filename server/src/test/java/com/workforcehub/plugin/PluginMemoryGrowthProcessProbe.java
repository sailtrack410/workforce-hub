package com.workforcehub.plugin;

import ch.qos.logback.classic.LoggerContext;
import com.workforcehub.plugin.runtime.PluginPermissionService;
import com.workforcehub.plugin.runtime.WorkforcePluginManager;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginState;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class PluginMemoryGrowthProcessProbe {

    private static final String PLUGIN_ID = "memory-probe-plugin";
    private static final int WARMUP_CYCLES = 20;
    private static final int MEASURED_CYCLES = 100;
    private static final int BATCH_SIZE = 20;
    private static final int SAMPLE_COUNT = 3;
    private static final MemoryMXBean MEMORY = ManagementFactory.getMemoryMXBean();
    private static final GarbageCollectorMXBean FULL_GC = findFullGcCollector();

    private PluginMemoryGrowthProcessProbe() {
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 2, "探针参数必须包含插件 JAR 和插件目录");
        require(Runtime.version().feature() == 17, "内存增长探针必须运行在 JDK 17");
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext) {
            loggerContext.reset();
        }

        Path pluginJar = Path.of(args[0]).toAbsolutePath().normalize();
        WorkforcePluginManager manager = new WorkforcePluginManager(
            Path.of(args[1]).toAbsolutePath().normalize().toString(),
            new PluginPermissionService(new JdbcTemplate())
        );
        List<WeakReference<ClassLoader>> classLoaders = new ArrayList<>(
            WARMUP_CYCLES + MEASURED_CYCLES
        );
        long[] baselineSamples = new long[SAMPLE_COUNT];
        long[] batchSamples = new long[MEASURED_CYCLES / BATCH_SIZE];
        long[] tailSamples = new long[SAMPLE_COUNT];

        try {
            for (int cycle = 0; cycle < WARMUP_CYCLES; cycle++) {
                classLoaders.add(runLifecycleCycle(manager, pluginJar));
            }
            require(awaitCollected(classLoaders, WARMUP_CYCLES) == WARMUP_CYCLES,
                "预热阶段 PluginClassLoader 未全部回收");
            takeSamples(baselineSamples);

            for (int batch = 0; batch < batchSamples.length; batch++) {
                for (int cycle = 0; cycle < BATCH_SIZE; cycle++) {
                    classLoaders.add(runLifecycleCycle(manager, pluginJar));
                }
                batchSamples[batch] = usedHeapAfterGc();
            }
            takeSamples(tailSamples);

            long baselineMedian = median(baselineSamples);
            long tailMedian = median(tailSamples);
            long delta = tailMedian - baselineMedian;
            int collectedClassLoaders = awaitCollected(
                classLoaders,
                WARMUP_CYCLES + MEASURED_CYCLES
            );
            Reference.reachabilityFence(manager);

            System.out.println(csv(
                baselineSamples,
                batchSamples,
                tailSamples,
                baselineMedian,
                tailMedian,
                delta,
                collectedClassLoaders
            ));
        } finally {
            manager.close();
            Reference.reachabilityFence(manager);
        }
    }

    private static WeakReference<ClassLoader> runLifecycleCycle(
            WorkforcePluginManager manager,
            Path pluginJar) {
        String pluginId = manager.loadPlugin(pluginJar);
        require(PLUGIN_ID.equals(pluginId), "插件 ID 不匹配: " + pluginId);
        require(manager.startPlugin(pluginId) == PluginState.STARTED, "插件启动失败");
        ClassLoader loadedClassLoader = manager.getPluginClassLoader(pluginId);
        require(loadedClassLoader instanceof PluginClassLoader, "插件 ClassLoader 类型不正确");
        PluginClassLoader pluginClassLoader = (PluginClassLoader) loadedClassLoader;

        require(manager.stopPlugin(pluginId) == PluginState.STOPPED, "插件停止失败");
        require(manager.unloadPlugin(pluginId), "插件卸载失败");
        require(pluginClassLoader.isClosed(), "插件 ClassLoader 未关闭");
        require(manager.getPlugin(pluginId) == null, "manager 仍保留插件 wrapper");
        require(manager.getPluginClassLoader(pluginId) == null, "manager 仍保留插件 ClassLoader");
        return new WeakReference<>(pluginClassLoader);
    }

    private static void takeSamples(long[] samples) throws InterruptedException {
        for (int index = 0; index < samples.length; index++) {
            samples[index] = usedHeapAfterGc();
        }
    }

    private static long usedHeapAfterGc() throws InterruptedException {
        long previousCount = FULL_GC.getCollectionCount();
        boolean observed = false;
        for (int attempt = 0; attempt < 3; attempt++) {
            MEMORY.gc();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            long currentCount = FULL_GC.getCollectionCount();
            while (currentCount <= previousCount && System.nanoTime() < deadline) {
                Thread.sleep(10);
                currentCount = FULL_GC.getCollectionCount();
            }
            observed |= currentCount > previousCount;
            previousCount = currentCount;
        }
        require(observed, "有界显式 GC 未发生");
        return MEMORY.getHeapMemoryUsage().getUsed();
    }

    private static int awaitCollected(
            List<WeakReference<ClassLoader>> references,
            int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        int collected = countCollected(references);
        while (collected < expected && System.nanoTime() < deadline) {
            usedHeapAfterGc();
            collected = countCollected(references);
        }
        return collected;
    }

    private static int countCollected(List<WeakReference<ClassLoader>> references) {
        int collected = 0;
        for (WeakReference<ClassLoader> reference : references) {
            if (reference.get() == null) {
                collected++;
            }
        }
        return collected;
    }

    private static GarbageCollectorMXBean findFullGcCollector() {
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            for (String poolName : collector.getMemoryPoolNames()) {
                if (poolName.contains("Tenured")) {
                    return collector;
                }
            }
        }
        throw new IllegalStateException("未发现 SerialGC 老年代收集器");
    }

    private static long median(long[] samples) {
        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static String csv(
            long[] baselineSamples,
            long[] batchSamples,
            long[] tailSamples,
            long baselineMedian,
            long tailMedian,
            long delta,
            int collectedClassLoaders) {
        StringBuilder result = new StringBuilder();
        append(result, baselineSamples);
        append(result, batchSamples);
        append(result, tailSamples);
        append(result, baselineMedian);
        append(result, tailMedian);
        append(result, delta);
        append(result, collectedClassLoaders);
        return result.substring(1);
    }

    private static void append(StringBuilder result, long[] values) {
        for (long value : values) {
            append(result, value);
        }
    }

    private static void append(StringBuilder result, long value) {
        result.append(',').append(value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
