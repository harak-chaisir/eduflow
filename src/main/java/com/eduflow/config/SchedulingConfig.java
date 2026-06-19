package com.eduflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Enables scheduled jobs (SLA breach detection — PRD §12, FR-8) and asynchronous
 * execution (used by the notification module's email dispatch — Phase 5).
 *
 * <p>Provides a {@link Clock} bean so time-dependent services can be unit-tested with a
 * fixed clock.</p>
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
