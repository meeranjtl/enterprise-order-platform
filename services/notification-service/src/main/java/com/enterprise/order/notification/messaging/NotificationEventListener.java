package com.enterprise.order.notification.messaging;

import com.enterprise.order.notification.service.NotificationService;
import com.enterprise.order.shared.events.OrderCreatedEvent;
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

/**
 * Consumes order, payment and shipping events and maps them to customer
 * notifications. Runs in its own consumer group so it never disturbs the saga
 * consumers. Exceptions propagate to the shared error handler → DLQ after retries.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private static final String EVENT_TYPE_HEADER = "eventType";

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @KafkaListener(topics = "order-events", groupId = "notification-service-group")
    public void onOrderCreated(ConsumerRecord<String, String> record) throws Exception {
        OrderCreatedEvent event = objectMapper.readValue(record.value(), OrderCreatedEvent.class);
        log.info("Notification listener: OrderCreatedEvent orderId={}", event.getOrderId());
        notificationService.handleOrderCreated(event);
    }

    @KafkaListener(topics = "payment-events", groupId = "notification-service-group")
    public void onPaymentProcessed(ConsumerRecord<String, String> record) throws Exception {
        PaymentProcessedEvent event = objectMapper.readValue(record.value(), PaymentProcessedEvent.class);
        log.info("Notification listener: PaymentProcessedEvent orderId={} status={}",
                event.getOrderId(), event.getStatus());

        if (event.getStatus() == PaymentProcessedEvent.PaymentStatus.COMPLETED) {
            notificationService.handlePaymentCompleted(event);
        }
    }

    @KafkaListener(topics = "shipping-events", groupId = "notification-service-group")
    public void onShippingEvent(ConsumerRecord<String, String> record) throws Exception {
        String eventType = header(record, EVENT_TYPE_HEADER);

        if (ShipmentCreatedEvent.EVENT_TYPE.equals(eventType)) {
            ShipmentCreatedEvent event = objectMapper.readValue(record.value(), ShipmentCreatedEvent.class);
            log.info("Notification listener: ShipmentCreatedEvent orderId={}", event.getOrderId());
            notificationService.handleShipmentCreated(event);
        } else if (ShipmentDeliveredEvent.EVENT_TYPE.equals(eventType)) {
            ShipmentDeliveredEvent event = objectMapper.readValue(record.value(), ShipmentDeliveredEvent.class);
            log.info("Notification listener: ShipmentDeliveredEvent orderId={}", event.getOrderId());
            notificationService.handleShipmentDelivered(event);
        } else {
            log.warn("Notification listener: unrecognized shipping eventType={} on topic={}",
                    eventType, record.topic());
        }
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
