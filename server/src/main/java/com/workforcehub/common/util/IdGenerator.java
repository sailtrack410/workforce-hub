package com.workforcehub.common.util;

import java.util.concurrent.atomic.AtomicLong;

public final class IdGenerator {

    private static final AtomicLong LAST_ID = new AtomicLong(System.currentTimeMillis() << 20);

    private IdGenerator() {
    }

    public static long nextId() {
        long now = System.currentTimeMillis() << 20;
        while (true) {
            long current = LAST_ID.get();
            long next = Math.max(now, current + 1);
            if (LAST_ID.compareAndSet(current, next)) {
                return next;
            }
        }
    }

    public static String nextIdString() {
        return Long.toString(nextId());
    }
}
