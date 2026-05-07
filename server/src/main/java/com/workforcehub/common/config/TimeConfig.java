package com.workforcehub.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 系统时间配置。
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
