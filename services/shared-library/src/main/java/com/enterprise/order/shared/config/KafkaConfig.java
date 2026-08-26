package com.enterprise.order.shared.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    // Default keeps services that do not declare spring.kafka.* (e.g. customer-service)
    // bootable; Docker Compose overrides this via SPRING_KAFKA_BOOTSTRAP_SERVERS.
    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    // Order Event Topics
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderEventsDlqTopic() {
        return TopicBuilder.name("order-events-dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }

    // Inventory Event Topics
    @Bean
    public NewTopic inventoryEventsTopic() {
        return TopicBuilder.name("inventory-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic inventoryEventsDlqTopic() {
        return TopicBuilder.name("inventory-events-dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }

    // Payment Event Topics
    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name("payment-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentEventsDlqTopic() {
        return TopicBuilder.name("payment-events-dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }

    // Shipping Event Topics (Phase 9)
    @Bean
    public NewTopic shippingEventsTopic() {
        return TopicBuilder.name("shipping-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic shippingEventsDlqTopic() {
        return TopicBuilder.name("shipping-events-dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }

    // Notification Event Topics (Phase 9)
    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name("notification-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationEventsDlqTopic() {
        return TopicBuilder.name("notification-events-dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }

    // Async Request/Reply Topics (Phase 9): shipping asks inventory for the
    // packing list; inventory replies on the reply topic.
    @Bean
    public NewTopic inventoryShippingRequestEventsTopic() {
        return TopicBuilder.name("inventory-shipping-request-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic inventoryShippingRequestEventsDlqTopic() {
        return TopicBuilder.name("inventory-shipping-request-events-dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic inventoryShippingReplyEventsTopic() {
        return TopicBuilder.name("inventory-shipping-reply-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic inventoryShippingReplyEventsDlqTopic() {
        return TopicBuilder.name("inventory-shipping-reply-events-dlq")
                .partitions(1)
                .replicas(1)
                .build();
    }

    // Consumer Error Handler with DLQ routing and retry policy.
    // After the retry budget is exhausted the DeadLetterPublishingRecoverer routes
    // the record to <original-topic>-dlq (default destination resolver), where the
    // DeadLetterQueueHandler logs it for manual replay.
    @Bean
    @SuppressWarnings("rawtypes")
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        errorHandler.addRetryableExceptions(Exception.class);
        return errorHandler;
    }
}
