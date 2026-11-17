package com.workforcehub.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 考勤测试使用固定系统时间，避免实时打卡语义受运行日期影响。
 */
@TestConfiguration
public class FixedClockTestConfiguration {

    @Bean
    @Primary
    public MutableTestClock fixedClock() {
        return new MutableTestClock(
            ZoneId.of("Asia/Shanghai"),
            LocalDateTime.of(2026, 5, 7, 9, 0)
        );
    }
}
