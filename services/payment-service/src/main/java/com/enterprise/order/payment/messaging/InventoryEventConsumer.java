package com.enterprise.order.payment.messaging;

import com.enterprise.order.payment.dto.CreatePaymentRequest;
import com.enterprise.order.payment.entity.PaymentMethod;
import com.enterprise.order.payment.service.PaymentService;
import com.enterprise.order.shared.events.InventoryReservedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;
    private final RestTemplate restTemplate = new RestTemplate();

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

            // Fetch order details from Order Service
            String orderUrl = String.format("http://order-service:8083/api/v1/orders/%s", event.getOrderId());
            JsonNode resp = restTemplate.getForObject(orderUrl, JsonNode.class);
            if (resp == null || resp.get("data") == null) {
                log.error("Failed to fetch order {} for payment processing", event.getOrderId());
                return;
            }
            JsonNode order = resp.get("data");
            Long orderId = Long.valueOf(order.get("id").asText());
            Long customerId = Long.valueOf(order.get("customerId").asText());
            double amount = order.get("totalAmount").asDouble();

            CreatePaymentRequest req = new CreatePaymentRequest();
            req.setOrderId(orderId);
            req.setCustomerId(customerId);
            req.setAmount(java.math.BigDecimal.valueOf(amount));
            req.setMethod(PaymentMethod.CREDIT_CARD);

            // Use orderId as idempotency key via PaymentService.create flows if available
            paymentService.create(req);
            log.info("Triggered payment creation for order {} amount={}", orderId, amount);
        } catch (Exception e) {
            log.error("Failed to handle InventoryReservedEvent", e);
        }
    }
}
