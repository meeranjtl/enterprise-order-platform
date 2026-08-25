package com.enterprise.order.shared.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Spring Boot ships no built-in Kafka health contributor (unlike DataSource) — verified
 * against spring-boot-actuator-autoconfigure 3.3.0, which has KafkaMetricsAutoConfiguration
 * but no health indicator. Reuses the KafkaAdmin bean already defined in KafkaConfig to
 * probe cluster connectivity; AbstractHealthIndicator.health() turns a thrown exception
 * into a "down" status automatically.
 */
@Component
public class KafkaHealthIndicator extends AbstractHealthIndicator {

    private final KafkaAdmin kafkaAdmin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            String clusterId = adminClient.describeCluster().clusterId().get(5, TimeUnit.SECONDS);
            builder.up().withDetail("clusterId", clusterId);
        }
    }
}
