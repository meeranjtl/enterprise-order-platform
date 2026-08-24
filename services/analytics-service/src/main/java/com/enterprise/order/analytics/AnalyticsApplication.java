package com.enterprise.order.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Analytics Service entry point (Phase 10).
 *
 * Base packages cover com.enterprise.order.* so shared-library components
 * (Kafka config, DLQ handler, exception advice) are picked up.
 * JPA repository/entity scanning is widened in {@code config.JpaConfig}.
 *
 * Analytics is a pure read-side consumer: it never publishes events, so the
 * shared OutboxPoller finds nothing to do. Scheduling is enabled for the
 * metrics reconciliation sweep (see service.MetricsReconciliationJob), which
 * heals the rare race where two concurrent consumer transactions miss each
 * other's uncommitted writes.
 */
@SpringBootApplication(scanBasePackages = "com.enterprise.order")
@EnableScheduling
public class AnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApplication.class, args);
    }
}
