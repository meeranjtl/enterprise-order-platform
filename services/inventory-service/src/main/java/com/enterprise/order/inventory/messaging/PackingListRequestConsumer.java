package com.enterprise.order.inventory.messaging;

import com.enterprise.order.inventory.service.InventoryService;
import com.enterprise.order.shared.events.PackingListRequestedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Async request/reply pattern (Phase 9): consumes shipping's packing-list request
 * and replies with the reserved items on inventory-shipping-reply-events.
 * Exceptions propagate to the shared error handler → DLQ after retries.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PackingListRequestConsumer {

    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;

    @KafkaListener(topics = "inventory-shipping-request-events", groupId = "inventory-service-group")
    public void handlePackingListRequest(ConsumerRecord<String, String> record) throws Exception {
        PackingListRequestedEvent event = objectMapper.readValue(record.value(), PackingListRequestedEvent.class);
        log.info("Received PackingListRequestedEvent orderId={} shipmentId={}",
                event.getOrderId(), event.getShipmentId());

        inventoryService.providePackingList(event);
    }
}
