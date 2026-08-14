package com.enterprise.order.notification.messaging;

import com.enterprise.order.notification.service.NotificationService;
import com.enterprise.order.shared.events.OrderCreatedEvent;
import com.enterprise.order.shared.events.ShipmentCreatedEvent;
import com.enterprise.order.shared.events.ShipmentDeliveredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationEventListener listener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        listener = new NotificationEventListener(objectMapper, notificationService);
    }

    @Test
    void orderEventIsForwardedToService() throws Exception {
        OrderCreatedEvent event = OrderCreatedEvent.builder().orderId("100").orderNumber("ORD-100").build();
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("order-events", 0, 0L, "100", objectMapper.writeValueAsString(event));

        listener.onOrderCreated(record);

        verify(notificationService).handleOrderCreated(any(OrderCreatedEvent.class));
    }

    @Test
    void shippingCreatedHeaderDispatchesToShipmentCreatedHandler() throws Exception {
        ShipmentCreatedEvent event = ShipmentCreatedEvent.builder().orderId("100").trackingNumber("TRK-1").build();
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("shipping-events", 0, 0L, "100", objectMapper.writeValueAsString(event));
        record.headers().add("eventType", ShipmentCreatedEvent.EVENT_TYPE.getBytes(StandardCharsets.UTF_8));

        listener.onShippingEvent(record);

        verify(notificationService).handleShipmentCreated(any(ShipmentCreatedEvent.class));
        verify(notificationService, never()).handleShipmentDelivered(any());
    }

    @Test
    void shippingDeliveredHeaderDispatchesToShipmentDeliveredHandler() throws Exception {
        ShipmentDeliveredEvent event = ShipmentDeliveredEvent.builder().orderId("100").trackingNumber("TRK-1").build();
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("shipping-events", 0, 0L, "100", objectMapper.writeValueAsString(event));
        record.headers().add("eventType", ShipmentDeliveredEvent.EVENT_TYPE.getBytes(StandardCharsets.UTF_8));

        listener.onShippingEvent(record);

        verify(notificationService).handleShipmentDelivered(any(ShipmentDeliveredEvent.class));
        verify(notificationService, never()).handleShipmentCreated(any());
    }

    @Test
    void unknownShippingEventTypeIsIgnored() throws Exception {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("shipping-events", 0, 0L, "100", "{}");
        record.headers().add("eventType", "SomeUnknownType".getBytes(StandardCharsets.UTF_8));

        listener.onShippingEvent(record);

        verifyNoInteractions(notificationService);
    }
}
