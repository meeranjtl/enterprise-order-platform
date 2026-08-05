package com.enterprise.order.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Payment Service entry point (Phase 7, Phase 8 saga participation).
 *
 * Base packages cover com.enterprise.order.* so shared-library components
 * (outbox poller/publisher, Kafka config, DLQ handler, exception advice) are picked up.
 * JPA repository/entity scanning is widened in {@code config.JpaConfig}; scheduling drives
 * the outbox poller (PaymentProcessedEvent publishing) and the failed-payment retry scanner.
 */
@SpringBootApplication(scanBasePackages = "com.enterprise.order")
@EnableScheduling
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
