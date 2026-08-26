package com.enterprise.order.payment.messaging;

import com.enterprise.order.payment.entity.OrderSnapshot;
import com.enterprise.order.payment.repository.OrderSnapshotRepository;
import com.enterprise.order.shared.events.OrderCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Phase 14: feeds OrderSnapshot so InventoryEventConsumer can look up an order's
// customerId/totalAmount locally instead of calling order-service synchronously.
// See docs/saga.md#known-issue-an-internal-call-outside-the-saga.
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final ObjectMapper objectMapper;
    private final OrderSnapshotRepository orderSnapshotRepository;

    @KafkaListener(topics = "order-events", groupId = "payment-service-group")
    @Transactional
    public void handleOrderCreated(ConsumerRecord<String, String> record) {
        try {
            OrderCreatedEvent event = objectMapper.readValue(record.value(), OrderCreatedEvent.class);
            Long orderId = event.getOrderId() == null ? null : Long.valueOf(event.getOrderId());
            if (orderId == null) {
                log.warn("OrderCreatedEvent missing orderId, skipping snapshot");
                return;
            }

            // Idempotent: at-least-once Kafka redelivery must not fail on a duplicate.
            if (orderSnapshotRepository.existsById(orderId)) {
                return;
            }

            orderSnapshotRepository.save(OrderSnapshot.builder()
                    .orderId(orderId)
                    .customerId(Long.valueOf(event.getCustomerId()))
                    .totalAmount(BigDecimal.valueOf(event.getTotalAmount()))
                    .build());
            log.info("Stored order snapshot for orderId={}", orderId);
        } catch (Exception e) {
            log.error("Failed to handle OrderCreatedEvent for snapshot", e);
        }
    }
}
