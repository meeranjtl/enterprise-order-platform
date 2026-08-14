package com.enterprise.order.order.saga;

import com.enterprise.order.order.service.OrderService;
import com.enterprise.order.shared.events.InventoryReservedEvent;
import com.enterprise.order.shared.events.PaymentProcessedEvent;
import com.enterprise.order.shared.events.ShipmentCreatedEvent;
import com.enterprise.order.shared.events.ShipmentDeliveredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

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

    // Phase 9: shipping closes the saga. shipping-events carries two event types,
    // so we dispatch on the eventType header written by OutboxPublisher.
    @KafkaListener(topics = "shipping-events", groupId = "order-service-group")
    public void handleShippingEvent(ConsumerRecord<String, String> record) {
        try {
            String eventType = header(record, "eventType");

            if (ShipmentCreatedEvent.EVENT_TYPE.equals(eventType)) {
                ShipmentCreatedEvent event = objectMapper.readValue(record.value(), ShipmentCreatedEvent.class);
                log.info("OrderSaga: received ShipmentCreatedEvent orderId={} tracking={}",
                        event.getOrderId(), event.getTrackingNumber());
                Long orderId = event.getOrderId() == null ? null : Long.valueOf(event.getOrderId());
                if (orderId != null) {
                    orderService.updateStatus(orderId, "SHIPPED");
                }
            } else if (ShipmentDeliveredEvent.EVENT_TYPE.equals(eventType)) {
                ShipmentDeliveredEvent event = objectMapper.readValue(record.value(), ShipmentDeliveredEvent.class);
                log.info("OrderSaga: received ShipmentDeliveredEvent orderId={} tracking={}",
                        event.getOrderId(), event.getTrackingNumber());
                Long orderId = event.getOrderId() == null ? null : Long.valueOf(event.getOrderId());
                if (orderId != null) {
                    orderService.updateStatus(orderId, "COMPLETED");
                }
            } else {
                log.warn("OrderSaga: unrecognized shipping eventType={} on topic={}", eventType, record.topic());
            }
        } catch (Exception e) {
            log.error("Failed to process shipping event in saga", e);
        }
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
