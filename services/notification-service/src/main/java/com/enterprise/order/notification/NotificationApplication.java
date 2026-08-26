package com.enterprise.order.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Notification Service entry point (Phase 9).
 *
 * Base packages cover com.enterprise.order.* so shared-library components
 * (outbox poller/publisher, Kafka config, DLQ handler, exception advice) are picked up.
 * JPA repository/entity scanning is widened in {@code config.JpaConfig}; scheduling drives
 * the outbox poller (NotificationSentEvent publishing).
 */
@SpringBootApplication(scanBasePackages = "com.enterprise.order")
@EnableScheduling
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
