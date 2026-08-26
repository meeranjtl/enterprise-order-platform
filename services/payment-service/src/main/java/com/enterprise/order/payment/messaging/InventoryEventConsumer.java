package com.enterprise.order.payment.messaging;

import com.enterprise.order.payment.dto.CreatePaymentRequest;
import com.enterprise.order.payment.entity.OrderSnapshot;
import com.enterprise.order.payment.entity.PaymentMethod;
import com.enterprise.order.payment.repository.OrderSnapshotRepository;
import com.enterprise.order.payment.service.PaymentService;
import com.enterprise.order.shared.events.InventoryReservedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;
    private final OrderSnapshotRepository orderSnapshotRepository;

    @KafkaListener(topics = "inventory-events", groupId = "payment-service-group")
    public void handleInventoryReserved(ConsumerRecord<String, String> record) {
        try {
            String payload = record.value();
            InventoryReservedEvent event = objectMapper.readValue(payload, InventoryReservedEvent.class);
            log.info("Received InventoryReservedEvent reservationId={} orderId={}", event.getReservationId(), event.getOrderId());

            if (event.getStatus() != InventoryReservedEvent.ReservationStatus.CONFIRMED) {
                log.info("Inventory event not confirmed, skipping payment for reservationId={}", event.getReservationId());
                return;
            }

            Long orderId = Long.valueOf(event.getOrderId());
            // Phase 14: was a synchronous, unauthenticated RestTemplate call to
            // order-service — the exact Kafka-only anti-pattern Phase 8 forbids, and
            // broken by Phase 12's auth on every invocation. order-events is causally
            // guaranteed to precede inventory-events for the same order (inventory-service
            // itself must consume OrderCreatedEvent before it can publish
            // InventoryReservedEvent), so the snapshot is expected to already exist.
            OrderSnapshot snapshot = orderSnapshotRepository.findById(orderId).orElse(null);
            if (snapshot == null) {
                log.error("No order snapshot found for orderId={}, cannot process payment", orderId);
                return;
            }

            CreatePaymentRequest req = new CreatePaymentRequest();
            req.setOrderId(orderId);
            req.setCustomerId(snapshot.getCustomerId());
            req.setAmount(snapshot.getTotalAmount());
            req.setMethod(PaymentMethod.CREDIT_CARD);

            paymentService.create(req);
            log.info("Triggered payment creation for order {} amount={}", orderId, snapshot.getTotalAmount());
        } catch (Exception e) {
            log.error("Failed to handle InventoryReservedEvent", e);
        }
    }
}
