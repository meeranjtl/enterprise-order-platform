package com.enterprise.order.customer.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Widens Spring Data JPA scanning to the whole com.enterprise.order tree.
 *
 * The component scan includes shared-library, whose OutboxPublisher bean depends on
 * OutboxEventRepository. By default Spring Data only scans the application class's own
 * package, so the scans are widened here. Kept in a separate configuration class (not on
 * the application class) so @WebMvcTest slices do not try to bootstrap JPA infrastructure.
 */
@Configuration
@EntityScan(basePackages = "com.enterprise.order")
@EnableJpaRepositories(basePackages = "com.enterprise.order")
public class JpaConfig {
}
