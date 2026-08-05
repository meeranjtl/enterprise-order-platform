package com.enterprise.order.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Order Service entry point (Phase 5, Phase 8 saga orchestration).
 *
 * Base packages cover com.enterprise.order.* so shared-library components
 * (outbox poller/publisher, Kafka config, DLQ handler, exception advice) are picked up.
 * JPA repository/entity scanning is widened in {@code config.JpaConfig}; scheduling drives
 * the outbox poller that publishes OrderCreatedEvent to Kafka.
 */
@SpringBootApplication(scanBasePackages = "com.enterprise.order")
@EnableScheduling
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
