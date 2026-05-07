package com.workforcehub.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 集成测试可控时钟。
 */
public class MutableTestClock extends Clock {

    private final ZoneId zone;
    private final AtomicReference<Instant> instant;

    public MutableTestClock(ZoneId zone, LocalDateTime initialTime) {
        this.zone = zone;
        this.instant = new AtomicReference<>(initialTime.atZone(zone).toInstant());
    }

    public void setTime(LocalDateTime time) {
        instant.set(time.atZone(zone).toInstant());
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableTestClock(zone, LocalDateTime.ofInstant(instant.get(), zone));
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
