package com.enterprise.order.shipping.messaging;

import com.enterprise.order.shared.events.PackingListProvidedEvent;
import com.enterprise.order.shipping.service.ShippingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Async request/reply pattern (Phase 9): consumes inventory's packing-list reply
 * and completes the shipment (PENDING → SHIPPED + ShipmentCreatedEvent).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PackingListReplyConsumer {

    private final ObjectMapper objectMapper;
    private final ShippingService shippingService;

    @KafkaListener(topics = "inventory-shipping-reply-events", groupId = "shipping-service-group")
    public void handlePackingListProvided(ConsumerRecord<String, String> record) throws Exception {
        PackingListProvidedEvent event = objectMapper.readValue(record.value(), PackingListProvidedEvent.class);
        log.info("Received PackingListProvidedEvent orderId={} items={}",
                event.getOrderId(), event.getItems() == null ? 0 : event.getItems().size());

        shippingService.applyPackingList(event);
    }
}
