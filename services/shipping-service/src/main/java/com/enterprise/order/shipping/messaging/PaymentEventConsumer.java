package com.enterprise.order.shipping.messaging;

import com.enterprise.order.shared.events.PaymentProcessedEvent;
import com.enterprise.order.shipping.service.ShippingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Phase 9 saga step: a COMPLETED payment starts fulfillment.
 *
 * Exceptions are intentionally NOT swallowed here — after the shared error
 * handler's retry budget the record is routed to payment-events-dlq
 * (DeadLetterPublishingRecoverer) instead of being silently dropped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final ObjectMapper objectMapper;
    private final ShippingService shippingService;

    @KafkaListener(topics = "payment-events", groupId = "shipping-service-group")
    public void handlePaymentEvent(ConsumerRecord<String, String> record) throws Exception {
        PaymentProcessedEvent event = objectMapper.readValue(record.value(), PaymentProcessedEvent.class);
        log.info("Received PaymentProcessedEvent orderId={} status={}", event.getOrderId(), event.getStatus());

        if (event.getStatus() != PaymentProcessedEvent.PaymentStatus.COMPLETED) {
            return;
        }
        if (event.getOrderId() == null || event.getOrderId().isBlank()) {
            log.warn("PaymentProcessedEvent without orderId, skipping");
            return;
        }

        shippingService.createFromPayment(event);
    }
}
