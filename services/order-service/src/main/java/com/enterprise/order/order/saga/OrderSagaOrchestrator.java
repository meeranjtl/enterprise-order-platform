package com.enterprise.order.order.saga;

import com.enterprise.order.order.service.OrderService;
import com.enterprise.order.shared.events.InventoryReservedEvent;
import com.enterprise.order.shared.events.PaymentProcessedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {

    private final ObjectMapper objectMapper;
    private final OrderService orderService;

    @KafkaListener(topics = "inventory-events", groupId = "order-service-group")
    public void handleInventoryEvent(ConsumerRecord<String, String> record) {
        try {
            String payload = record.value();
            InventoryReservedEvent event = objectMapper.readValue(payload, InventoryReservedEvent.class);
            log.info("OrderSaga: received InventoryReservedEvent orderId={} status={}", event.getOrderId(), event.getStatus());

            Long orderId = event.getOrderId() == null ? null : Long.valueOf(event.getOrderId());
            if (orderId != null) {
                if (event.getStatus() == InventoryReservedEvent.ReservationStatus.CONFIRMED) {
                    orderService.updateStatus(orderId, "PAYMENT_PENDING");
                } else if (event.getStatus() == InventoryReservedEvent.ReservationStatus.FAILED) {
                    orderService.updateStatus(orderId, "FAILED");
                }
            }
        } catch (Exception e) {
            log.error("Failed to process inventory event in saga", e);
        }
    }

    @KafkaListener(topics = "payment-events", groupId = "order-service-group")
    public void handlePaymentEvent(ConsumerRecord<String, String> record) {
        try {
            String payload = record.value();
            PaymentProcessedEvent event = objectMapper.readValue(payload, PaymentProcessedEvent.class);
            log.info("OrderSaga: received PaymentProcessedEvent orderId={} status={}", event.getOrderId(), event.getStatus());

            Long orderId = event.getOrderId() == null ? null : Long.valueOf(event.getOrderId());
            if (orderId != null) {
                if (event.getStatus() == PaymentProcessedEvent.PaymentStatus.COMPLETED) {
                    orderService.updateStatus(orderId, "PAYMENT_APPROVED");
                } else if (event.getStatus() == PaymentProcessedEvent.PaymentStatus.FAILED) {
                    orderService.updateStatus(orderId, "PAYMENT_REJECTED");
                }
            }
        } catch (Exception e) {
            log.error("Failed to process payment event in saga", e);
        }
    }
}
