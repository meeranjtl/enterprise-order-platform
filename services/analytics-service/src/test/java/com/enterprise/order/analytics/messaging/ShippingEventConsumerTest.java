package com.enterprise.order.analytics.messaging;

import com.enterprise.order.analytics.service.MetricsAggregationService;
import com.enterprise.order.shared.events.ShipmentCreatedEvent;
import com.enterprise.order.shared.events.ShipmentDeliveredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * shipping-events carries two event types on one topic; dispatch must happen on
 * the eventType header (Phase 9 pattern), and unknown headers must be ignored
 * rather than thrown (a foreign producer should not fill analytics' DLQ).
 */
@ExtendWith(MockitoExtension.class)
class ShippingEventConsumerTest {

    private ShippingEventConsumer consumer;
    private ObjectMapper objectMapper;

    @Mock
    private MetricsAggregationService metricsAggregationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new ShippingEventConsumer(objectMapper, metricsAggregationService);
    }

    @Test
    void shipmentCreatedHeader_routesToShipmentCreated() throws Exception {
        ShipmentCreatedEvent event = ShipmentCreatedEvent.builder()
                .orderId("101")
                .trackingNumber("TRK-1")
                .shippedAt(LocalDateTime.of(2026, 8, 22, 11, 0))
                .build();
        ConsumerRecord<String, String> record =
                record(ShipmentCreatedEvent.EVENT_TYPE, objectMapper.writeValueAsString(event));

        consumer.onShippingEvent(record);

        ArgumentCaptor<ShipmentCreatedEvent> captor = ArgumentCaptor.forClass(ShipmentCreatedEvent.class);
        verify(metricsAggregationService).recordShipmentCreated(captor.capture());
        assertEquals("101", captor.getValue().getOrderId());
    }

    @Test
    void shipmentDeliveredHeader_routesToShipmentDelivered() throws Exception {
        ShipmentDeliveredEvent event = ShipmentDeliveredEvent.builder()
                .orderId("101")
                .deliveredAt(LocalDateTime.of(2026, 8, 22, 16, 0))
                .build();
        ConsumerRecord<String, String> record =
                record(ShipmentDeliveredEvent.EVENT_TYPE, objectMapper.writeValueAsString(event));

        consumer.onShippingEvent(record);

        ArgumentCaptor<ShipmentDeliveredEvent> captor = ArgumentCaptor.forClass(ShipmentDeliveredEvent.class);
        verify(metricsAggregationService).recordShipmentDelivered(captor.capture());
        assertEquals("101", captor.getValue().getOrderId());
    }

    @Test
    void unknownEventType_isIgnored() throws Exception {
        consumer.onShippingEvent(record("SomeFutureEvent", "{}"));

        verifyNoInteractions(metricsAggregationService);
    }

    @Test
    void missingEventTypeHeader_isIgnored() throws Exception {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("shipping-events", 0, 0, "101", "{}");

        consumer.onShippingEvent(record);

        verifyNoInteractions(metricsAggregationService);
    }

    private ConsumerRecord<String, String> record(String eventType, String json) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("shipping-events", 0, 0, "101", json);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
