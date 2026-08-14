package com.enterprise.order.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Base packages cover com.enterprise.order.* so shared-library components
 * (outbox poller/publisher, Kafka config, DLQ handler, exception advice) are picked up.
 * JPA repository/entity scanning is widened in {@code config.JpaConfig} so shared-library's
 * OutboxEventRepository is available to the OutboxPublisher bean.
 *
 * Use scanBasePackages (not a separate @ComponentScan): test slices such as @WebMvcTest
 * apply their type-exclude filters through the @SpringBootApplication annotation, so
 * slices stay free of JPA/Kafka beans only when this form is used.
 */
@SpringBootApplication(scanBasePackages = "com.enterprise.order")
public class CustomerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CustomerApplication.class, args);
    }
}
