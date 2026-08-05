package com.enterprise.order.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Inventory Service entry point (Phase 6, Phase 8 saga participation).
 *
 * Base packages cover com.enterprise.order.* so shared-library components
 * (outbox poller/publisher, Kafka config, DLQ handler, exception advice) are picked up.
 * JPA repository/entity scanning is widened in {@code config.JpaConfig}; scheduling drives
 * the outbox poller that publishes InventoryReservedEvent to Kafka.
 */
@SpringBootApplication(scanBasePackages = "com.enterprise.order")
@EnableScheduling
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}
