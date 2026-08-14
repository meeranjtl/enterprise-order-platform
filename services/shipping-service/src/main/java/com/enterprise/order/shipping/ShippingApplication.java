package com.enterprise.order.shipping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Shipping Service entry point (Phase 9).
 *
 * Base packages cover com.enterprise.order.* so shared-library components
 * (outbox poller/publisher, Kafka config, DLQ handler, exception advice) are picked up.
 * JPA repository/entity scanning is widened in {@code config.JpaConfig}; scheduling drives
 * the outbox poller (PackingListRequested / ShipmentCreated / ShipmentDelivered publishing).
 */
@SpringBootApplication(scanBasePackages = "com.enterprise.order")
@EnableScheduling
public class ShippingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShippingApplication.class, args);
    }
}
