package com.enterprise.order.inventory.messaging;

import com.enterprise.order.inventory.dto.ReservationRequest;
import com.enterprise.order.inventory.service.InventoryService;
import com.enterprise.order.shared.events.OrderCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;

    @KafkaListener(topics = "order-events", groupId = "inventory-service-group")
    public void handleOrderCreated(ConsumerRecord<String, String> record) {
        try {
            String payload = record.value();
            OrderCreatedEvent event = objectMapper.readValue(payload, OrderCreatedEvent.class);
            log.info("Received OrderCreatedEvent for orderId={}", event.getOrderId());

            if (event.getOrderItems() == null || event.getOrderItems().isEmpty()) {
                log.warn("Order {} has no items, skipping reservation", event.getOrderId());
                return;
            }

            Long orderId = Long.valueOf(event.getOrderId());
            for (OrderCreatedEvent.OrderItem item : event.getOrderItems()) {
                ReservationRequest req = new ReservationRequest();
                req.setOrderId(orderId);
                req.setProductId(Long.valueOf(item.getProductId()));
                req.setQuantity(item.getQuantity());
                String idempotencyKey = event.getOrderId() + ":" + item.getProductId();

                try {
                    inventoryService.reserve(req, idempotencyKey);
                    log.info("Reserved product {} qty {} for order {}", item.getProductId(), item.getQuantity(), event.getOrderId());
                } catch (Exception e) {
                    log.error("Failed to reserve product {} for order {}: {}", item.getProductId(), event.getOrderId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle OrderCreatedEvent", e);
        }
    }
}
