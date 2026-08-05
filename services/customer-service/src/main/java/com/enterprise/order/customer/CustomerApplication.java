package com.enterprise.order.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

/**
 * The component scan includes shared-library. JPA repository/entity scanning is widened
 * to com.enterprise.order in {@code config.JpaConfig} so shared-library's
 * OutboxEventRepository is available to the OutboxPublisher bean.
 *
 * Use scanBasePackages (not a separate @ComponentScan): test slices such as @WebMvcTest
 * apply their type-exclude filters through the @SpringBootApplication annotation, so
 * slices stay free of JPA/Kafka beans only when this form is used.
 */
@SpringBootApplication(scanBasePackages = {
        "com.enterprise.order.shared",
        "com.enterprise.order.customer"
})
public class CustomerApplication {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(CustomerApplication.class, args);
    }
}
