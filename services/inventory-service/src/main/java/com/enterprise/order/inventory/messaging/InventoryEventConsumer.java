package com.enterprise.order.inventory.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "inventory.kafka", name = "enabled", havingValue = "true")
public class InventoryEventConsumer {

    @KafkaListener(topics = "order-events", groupId = "inventory-service")
    public void consume(String event) {
        log.info("Received order event for inventory processing: {}", event);
    }
}
