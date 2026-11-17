package com.workforcehub.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginMemoryGrowthIntegrationTest {

    private static final int PROCESS_RUNS = 3;
    private static final int RESULT_VALUE_COUNT = 15;
    private static final long MAX_HEAP_GROWTH_BYTES = 16L * 1024 * 1024;
    private static final int EXPECTED_COLLECTED_CLASS_LOADERS = 120;

    @TempDir
    Path tempDir;

    @Test
    void shouldKeepHeapGrowthWithinLimitAcrossRepeatedPluginReloads() throws Exception {
        Path pluginJar = buildMemoryProbePluginJar();
        List<Throwable> failures = new ArrayList<>();

        for (int run = 1; run <= PROCESS_RUNS; run++) {
            try {
                ProbeResult result = runProbe(run, pluginJar);
                System.out.printf(
                    "plugin-heap-run=%d baseline=%s batches=%s tail=%s baselineMedian=%d tailMedian=%d delta=%d collectedClassLoaders=%d%n",
                    run,
                    Arrays.toString(result.baselineSamples()),
                    Arrays.toString(result.batchSamples()),
                    Arrays.toString(result.tailSamples()),
                    result.baselineMedian(),
                    result.tailMedian(),
                    result.delta(),
                    result.collectedClassLoaders()
                );
                assertEquals(result.baselineMedian(), median(result.baselineSamples()), "基线中位数不一致");
                assertEquals(result.tailMedian(), median(result.tailSamples()), "末段中位数不一致");
                assertEquals(result.tailMedian() - result.baselineMedian(), result.delta(), "堆增量不一致");
                assertTrue(result.delta() <= MAX_HEAP_GROWTH_BYTES,
                    "进程级 Java heap 增长超过 16 MiB: " + result.delta());
                assertEquals(EXPECTED_COLLECTED_CLASS_LOADERS, result.collectedClassLoaders(),
                    "并非全部 PluginClassLoader 均已回收");
            } catch (Exception | AssertionError failure) {
                failures.add(new AssertionError("第 " + run + " 个隔离 JVM 失败", failure));
            }
        }

        if (!failures.isEmpty()) {
            AssertionError combined = new AssertionError(
                failures.size() + "/" + PROCESS_RUNS + " 个隔离 JVM 未通过插件堆增长回归"
            );
            failures.forEach(combined::addSuppressed);
            throw combined;
        }
    }

    private ProbeResult runProbe(int run, Path pluginJar) throws Exception {
        Path workingDirectory = Files.createDirectory(tempDir.resolve("memory-probe-run-" + run));
        Path output = tempDir.resolve("memory-probe-run-" + run + ".log");
        ProcessBuilder processBuilder = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-Xms256m",
            "-Xmx256m",
            "-XX:+UseSerialGC",
            "-XX:-HeapDumpOnOutOfMemoryError",
            "-cp",
            absoluteTestClasspath(),
            PluginMemoryGrowthProcessProbe.class.getName(),
            pluginJar.toAbsolutePath().normalize().toString(),
            workingDirectory.resolve("plugins").toString()
        );
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.environment().clear();
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(output.toFile());

        Process process = processBuilder.start();
        boolean finished;
        try {
            finished = process.waitFor(120, TimeUnit.SECONDS);
        } finally {
            if (process.isAlive()) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("子 JVM 强制终止失败");
                    }
                }
            }
        }
        if (!finished) {
            throw new AssertionError("子 JVM 超时\n" + readTail(output, 8192));
        }
        if (process.exitValue() != 0) {
            throw new AssertionError(
                "子 JVM 退出码为 " + process.exitValue() + "\n" + readTail(output, 8192)
            );
        }
        if (Files.size(output) > 16 * 1024) {
            throw new AssertionError("子 JVM 输出异常过大\n" + readTail(output, 8192));
        }

        String content = Files.readString(output, StandardCharsets.UTF_8).trim();
        String[] tokens = content.split(",", -1);
        assertEquals(RESULT_VALUE_COUNT, tokens.length, "子 JVM 输出格式不正确: " + content);
        long[] values = new long[tokens.length];
        for (int index = 0; index < tokens.length; index++) {
            values[index] = Long.parseLong(tokens[index]);
        }
        return new ProbeResult(
            Arrays.copyOfRange(values, 0, 3),
            Arrays.copyOfRange(values, 3, 8),
            Arrays.copyOfRange(values, 8, 11),
            values[11],
            values[12],
            values[13],
            values[14]
        );
    }

    private Path buildMemoryProbePluginJar() throws IOException {
        Path sourceFile = tempDir.resolve(
            "memory-probe-src/com/workforcehub/plugin/memoryprobe/MemoryProbePlugin.java"
        );
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, MEMORY_PROBE_PLUGIN_SOURCE, StandardCharsets.UTF_8);

        Path classes = Files.createDirectory(tempDir.resolve("memory-probe-classes"));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "内存增长测试需要 JDK，不能只使用 JRE");
        int result = compiler.run(
            null,
            null,
            null,
            "--release",
            "17",
            "-proc:none",
            "-classpath",
            absoluteTestClasspath(),
            "-d",
            classes.toString(),
            sourceFile.toString()
        );
        assertEquals(0, result, "内存探针插件源码编译失败");

        Files.writeString(classes.resolve("plugin.properties"), """
            plugin.id=memory-probe-plugin
            plugin.class=com.workforcehub.plugin.memoryprobe.MemoryProbePlugin
            plugin.version=0.1.0
            plugin.provider=workforce-hub
            plugin.description=Plugin memory growth probe
            """, StandardCharsets.UTF_8);

        Path jar = tempDir.resolve("memory-probe-plugin.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar));
             var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList()) {
                String entryName = classes.relativize(file).toString().replace('\\', '/');
                output.putNextEntry(new JarEntry(entryName));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
        return jar;
    }

    private String absoluteTestClasspath() {
        String configuredClasspath = System.getProperty(
            "surefire.test.class.path",
            System.getProperty("java.class.path")
        );
        Path launchDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return Arrays.stream(configuredClasspath.split(File.pathSeparator))
            .filter(entry -> !entry.isBlank())
            .map(Path::of)
            .map(path -> path.isAbsolute() ? path.normalize() : launchDirectory.resolve(path).normalize())
            .map(Path::toString)
            .collect(Collectors.joining(File.pathSeparator));
    }

    private long median(long[] samples) {
        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private String readTail(Path file, int maxBytes) throws IOException {
        if (!Files.exists(file)) {
            return "";
        }
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            int length = (int) Math.min(input.length(), maxBytes);
            byte[] bytes = new byte[length];
            input.seek(input.length() - length);
            input.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private record ProbeResult(
        long[] baselineSamples,
        long[] batchSamples,
        long[] tailSamples,
        long baselineMedian,
        long tailMedian,
        long delta,
        long collectedClassLoaders
    ) {
    }

    private static final String MEMORY_PROBE_PLUGIN_SOURCE = """
        package com.workforcehub.plugin.memoryprobe;

        import com.workforcehub.plugin.sdk.PluginContext;
        import com.workforcehub.plugin.sdk.WorkforcePlugin;
        import org.pf4j.PluginWrapper;

        public final class MemoryProbePlugin extends WorkforcePlugin {

            private byte[] retained;

            public MemoryProbePlugin(PluginWrapper wrapper, PluginContext context) {
                super(wrapper, context);
            }

            @Override
            protected void onStart() {
                retained = new byte[256 * 1024];
                registerCleanup(() -> retained = null);
            }
        }
        """;
}
