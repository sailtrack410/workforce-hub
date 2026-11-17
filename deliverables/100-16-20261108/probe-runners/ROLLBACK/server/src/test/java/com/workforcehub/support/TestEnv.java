package com.workforcehub.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试环境变量读取：优先 JVM 环境变量，其次仓库 .env 文件（gitignored，与
 * application.yml 的 spring.config.import 同源）。真实凭证只存 .env，不入库。
 */
public final class TestEnv {

    private static final Map<String, String> DOT_ENV = loadDotEnv();

    private TestEnv() {
    }

    /**
     * 必填环境变量：JVM 环境变量 → .env 文件 → 都没有则抛异常（测试快速失败，绝不使用硬编码口令）
     */
    public static String require(String primaryKey, String fallbackKey) {
        String value = pick(primaryKey, fallbackKey);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少数据库口令：请在仓库根目录 .env 配置 " + fallbackKey
                + "（或 TEST_" + fallbackKey + "），不要把真实凭证写入代码");
        }
        return value;
    }

    private static String pick(String primaryKey, String fallbackKey) {
        String value = System.getenv(primaryKey);
        if (value == null || value.isBlank()) {
            value = System.getenv(fallbackKey);
        }
        if (value == null || value.isBlank()) {
            value = DOT_ENV.get(primaryKey);
        }
        if (value == null || value.isBlank()) {
            value = DOT_ENV.get(fallbackKey);
        }
        return value;
    }

    public static String get(String key, String defaultValue) {
        String value = DOT_ENV.containsKey(key) ? DOT_ENV.get(key) : System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> values = new HashMap<>();
        // 与 application.yml 的 spring.config.import 同源：server 工作目录与仓库根各探测一次
        List<Path> candidates = List.of(Path.of(".env"), Path.of("../.env"));
        for (Path path : candidates) {
            if (!Files.isReadable(path)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(path)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                        continue;
                    }
                    int split = trimmed.indexOf('=');
                    String key = trimmed.substring(0, split).trim();
                    String value = trimmed.substring(split + 1).trim();
                    if (value.length() >= 2
                            && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    values.putIfAbsent(key, value);
                }
            } catch (IOException e) {
                throw new IllegalStateException("读取 .env 失败: " + path, e);
            }
        }
        return Map.copyOf(values);
    }
}
