package com.enterprise.order.analytics.messaging;

import com.enterprise.order.analytics.service.MetricsAggregationService;
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
 * Consumes the shipping-events topic, which carries TWO event types; dispatch
 * happens on the eventType header written by OutboxPublisher (Phase 9 pattern,
 * same as OrderSagaOrchestrator and NotificationEventListener).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShippingEventConsumer {

    private static final String EVENT_TYPE_HEADER = "eventType";

    private final ObjectMapper objectMapper;
    private final MetricsAggregationService metricsAggregationService;

    @KafkaListener(topics = "shipping-events", groupId = "analytics-service-group")
    public void onShippingEvent(ConsumerRecord<String, String> record) throws Exception {
        String eventType = header(record, EVENT_TYPE_HEADER);

        if (ShipmentCreatedEvent.EVENT_TYPE.equals(eventType)) {
            ShipmentCreatedEvent event = objectMapper.readValue(record.value(), ShipmentCreatedEvent.class);
            log.info("Analytics consumer: ShipmentCreatedEvent orderId={}", event.getOrderId());
            metricsAggregationService.recordShipmentCreated(event);
        } else if (ShipmentDeliveredEvent.EVENT_TYPE.equals(eventType)) {
            ShipmentDeliveredEvent event = objectMapper.readValue(record.value(), ShipmentDeliveredEvent.class);
            log.info("Analytics consumer: ShipmentDeliveredEvent orderId={}", event.getOrderId());
            metricsAggregationService.recordShipmentDelivered(event);
        } else {
            log.warn("Analytics consumer: unrecognized shipping eventType={} on topic={}",
                    eventType, record.topic());
        }
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
