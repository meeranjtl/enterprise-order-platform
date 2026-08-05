package com.enterprise.order.inventory.messaging;

import com.enterprise.order.inventory.service.InventoryService;
import com.enterprise.order.inventory.entity.InventoryTransaction;
import com.enterprise.order.inventory.repository.InventoryTransactionRepository;
import com.enterprise.order.shared.events.PaymentProcessedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;
    private final InventoryTransactionRepository transactionRepository;

    @KafkaListener(topics = "payment-events", groupId = "inventory-service-group")
    public void handlePaymentEvent(ConsumerRecord<String, String> record) {
        try {
            String payload = record.value();
            PaymentProcessedEvent event = objectMapper.readValue(payload, PaymentProcessedEvent.class);
            log.info("Received PaymentProcessedEvent orderId={} status={}", event.getOrderId(), event.getStatus());

            if (event.getStatus() == PaymentProcessedEvent.PaymentStatus.FAILED) {
                Long orderId = Long.valueOf(event.getOrderId());
                // find reserve transactions for this order and release each
                List<InventoryTransaction> reserves = transactionRepository.findAll().stream()
                        .filter(tx -> tx.getOrderId() != null && tx.getOrderId().equals(orderId) && tx.getType() == com.enterprise.order.inventory.entity.TransactionType.RESERVE)
                        .toList();

                for (InventoryTransaction tx : reserves) {
                    try {
                        var req = new com.enterprise.order.inventory.dto.ReservationRequest();
                        req.setOrderId(orderId);
                        req.setProductId(tx.getProductId());
                        req.setQuantity(tx.getQuantity());
                        String idempotencyKey = "payment-failure:" + event.getPaymentId() + ":" + tx.getId();
                        inventoryService.release(req, idempotencyKey);
                        log.info("Released reservation tx={} product={} qty={} for order {} due to payment failure", tx.getId(), tx.getProductId(), tx.getQuantity(), orderId);
                    } catch (Exception ex) {
                        log.error("Failed to release reservation tx={} for order {}: {}", tx.getId(), orderId, ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle PaymentProcessedEvent", e);
        }
    }
}
