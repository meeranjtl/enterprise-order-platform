package com.enterprise.order.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

/**
 * The component scan includes shared-library. JPA repository/entity scanning is widened
 * to com.enterprise.order in {@code config.JpaConfig} so shared-library's
 * OutboxEventRepository is available to the OutboxPublisher bean.
 */
@SpringBootApplication(scanBasePackages = "com.enterprise.order")
public class ProductApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}