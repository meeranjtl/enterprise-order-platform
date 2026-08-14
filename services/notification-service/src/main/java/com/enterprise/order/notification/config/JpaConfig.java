package com.enterprise.order.notification.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Widens Spring Data JPA scanning to the whole com.enterprise.order tree.
 *
 * By default Spring Data only scans the application class's own package, which would
 * miss shared-library's OutboxEventRepository / OutboxEvent entity. Kept in a separate
 * configuration class (not on the application class) so @WebMvcTest slices do not
 * try to bootstrap JPA infrastructure.
 */
@Configuration
@EntityScan(basePackages = "com.enterprise.order")
@EnableJpaRepositories(basePackages = "com.enterprise.order")
public class JpaConfig {
}
