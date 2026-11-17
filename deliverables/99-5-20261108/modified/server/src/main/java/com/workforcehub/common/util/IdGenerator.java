package com.workforcehub.common.util;

import java.util.LinkedHashMap;
import java.util.function.LongSupplier;

// 简化版雪花 ID：时间戳左移 20 位 + 自增序列，单机每毫秒可生成约 100 万个 ID
// 上行为历史实现说明；现使用共享数据库持久号段，不再依赖时间戳或单机序列。
public final class IdGenerator {

    // 生产号段由共享数据库持久分配，禁止在初始化失败时退回单机算法。
    private static final LinkedHashMap<Object, LongSupplier> GENERATORS = new LinkedHashMap<>();

    private IdGenerator() {
    }

    static synchronized void register(Object owner, LongSupplier generator) {
        GENERATORS.put(owner, generator);
    }

    static synchronized void unregister(Object owner) {
        GENERATORS.remove(owner);
    }

    public static synchronized long nextId() {
        LongSupplier generator = null;
        for (LongSupplier active : GENERATORS.values()) generator = active;
        if (generator == null) throw new IllegalStateException("ID generator database is not initialized");
        return generator.getAsLong();
    }

    public static String nextIdString() {
        return Long.toString(nextId());
    }
}
