package com.enterprise.order.shared.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class EventSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @Test
    void testOrderCreatedEventSerialization() throws Exception {
        // Arrange
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId("ORDER123");
        event.setOrderNumber("ORD-2024-001");
        event.setCustomerId("CUST456");
        event.setTotalAmount(999.99);
        event.setCreatedAt(LocalDateTime.now());

        OrderCreatedEvent.OrderItem item1 = new OrderCreatedEvent.OrderItem();
        item1.setProductId("PROD001");
        item1.setQuantity(2);
        item1.setUnitPrice(100.0);

        event.setOrderItems(Arrays.asList(item1));

        // Act
        String json = objectMapper.writeValueAsString(event);
        OrderCreatedEvent deserialized = objectMapper.readValue(json, OrderCreatedEvent.class);

        // Assert
        assertEquals("ORDER123", deserialized.getOrderId());
        assertEquals("ORD-2024-001", deserialized.getOrderNumber());
        assertEquals("CUST456", deserialized.getCustomerId());
        assertEquals(999.99, deserialized.getTotalAmount());
        assertEquals(1, deserialized.getOrderItems().size());
        assertEquals("PROD001", deserialized.getOrderItems().get(0).getProductId());
        assertEquals(2, deserialized.getOrderItems().get(0).getQuantity());
    }

    @Test
    void testInventoryReservedEventSerialization() throws Exception {
        // Arrange
        InventoryReservedEvent event = new InventoryReservedEvent();
        event.setReservationId("RES123");
        event.setOrderId("ORDER456");
        event.setProductId("PROD001");
        event.setQuantity(5);
        event.setStatus(InventoryReservedEvent.ReservationStatus.CONFIRMED);

        // Act
        String json = objectMapper.writeValueAsString(event);
        InventoryReservedEvent deserialized = objectMapper.readValue(json, InventoryReservedEvent.class);

        // Assert
        assertEquals("RES123", deserialized.getReservationId());
        assertEquals("ORDER456", deserialized.getOrderId());
        assertEquals("PROD001", deserialized.getProductId());
        assertEquals(5, deserialized.getQuantity());
        assertEquals(InventoryReservedEvent.ReservationStatus.CONFIRMED, deserialized.getStatus());
    }

    @Test
    void testPaymentProcessedEventSerialization() throws Exception {
        // Arrange
        PaymentProcessedEvent event = new PaymentProcessedEvent();
        event.setPaymentId("PAY123");
        event.setOrderId("ORDER789");
        event.setStatus(PaymentProcessedEvent.PaymentStatus.COMPLETED);
        event.setAmount(999.99);

        // Act
        String json = objectMapper.writeValueAsString(event);
        PaymentProcessedEvent deserialized = objectMapper.readValue(json, PaymentProcessedEvent.class);

        // Assert
        assertEquals("PAY123", deserialized.getPaymentId());
        assertEquals("ORDER789", deserialized.getOrderId());
        assertEquals(PaymentProcessedEvent.PaymentStatus.COMPLETED, deserialized.getStatus());
        assertEquals(999.99, deserialized.getAmount());
    }

    @Test
    void testOrderCreatedEventWithLargePayload() throws Exception {
        // Arrange
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId("ORDER123");
        event.setOrderNumber("ORD-2024-001");
        event.setCustomerId("CUST456");
        event.setTotalAmount(99999.99);

        // Add multiple items to simulate large payload
        java.util.List<OrderCreatedEvent.OrderItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            OrderCreatedEvent.OrderItem item = new OrderCreatedEvent.OrderItem();
            item.setProductId("PROD" + i);
            item.setQuantity(i + 1);
            item.setUnitPrice(100.0 + i);
            items.add(item);
        }
        event.setOrderItems(items);

        // Act
        String json = objectMapper.writeValueAsString(event);
        OrderCreatedEvent deserialized = objectMapper.readValue(json, OrderCreatedEvent.class);

        // Assert
        assertEquals(100, deserialized.getOrderItems().size());
        assertEquals("PROD0", deserialized.getOrderItems().get(0).getProductId());
        assertEquals("PROD99", deserialized.getOrderItems().get(99).getProductId());
    }

    @Test
    void testEventNullFieldsHandling() throws Exception {
        // Arrange
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId("ORDER123");
        event.setCustomerId(null);
        event.setTotalAmount(null);

        // Act
        String json = objectMapper.writeValueAsString(event);
        OrderCreatedEvent deserialized = objectMapper.readValue(json, OrderCreatedEvent.class);

        // Assert
        assertEquals("ORDER123", deserialized.getOrderId());
        assertNull(deserialized.getCustomerId());
        assertNull(deserialized.getTotalAmount());
    }

    // --- Phase 9 events ---

    @Test
    void testPackingListRequestedEventSerialization() throws Exception {
        PackingListRequestedEvent event = PackingListRequestedEvent.builder()
                .requestId("REQ1")
                .orderId("ORDER1")
                .shipmentId("SHIP1")
                .customerId("CUST1")
                .createdAt(LocalDateTime.now())
                .build();

        PackingListRequestedEvent deserialized =
                objectMapper.readValue(objectMapper.writeValueAsString(event), PackingListRequestedEvent.class);

        assertEquals("REQ1", deserialized.getRequestId());
        assertEquals("ORDER1", deserialized.getOrderId());
        assertEquals("SHIP1", deserialized.getShipmentId());
        assertEquals("CUST1", deserialized.getCustomerId());
        assertEquals(PackingListRequestedEvent.TOPIC, "inventory-shipping-request-events");
    }

    @Test
    void testPackingListProvidedEventSerialization() throws Exception {
        PackingListProvidedEvent event = PackingListProvidedEvent.builder()
                .requestId("REQ1")
                .orderId("ORDER1")
                .shipmentId("SHIP1")
                .items(Arrays.asList(
                        PackingListProvidedEvent.PackingItem.builder().productId("PROD1").quantity(2).build(),
                        PackingListProvidedEvent.PackingItem.builder().productId("PROD2").quantity(5).build()))
                .createdAt(LocalDateTime.now())
                .build();

        PackingListProvidedEvent deserialized =
                objectMapper.readValue(objectMapper.writeValueAsString(event), PackingListProvidedEvent.class);

        assertEquals("ORDER1", deserialized.getOrderId());
        assertEquals(2, deserialized.getItems().size());
        assertEquals("PROD1", deserialized.getItems().get(0).getProductId());
        assertEquals(5, deserialized.getItems().get(1).getQuantity());
        assertEquals(PackingListProvidedEvent.TOPIC, "inventory-shipping-reply-events");
    }

    @Test
    void testShipmentCreatedEventSerialization() throws Exception {
        ShipmentCreatedEvent event = ShipmentCreatedEvent.builder()
                .shipmentId("SHIP1")
                .orderId("ORDER1")
                .customerId("CUST1")
                .trackingNumber("TRK-ABC123")
                .shippedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        ShipmentCreatedEvent deserialized =
                objectMapper.readValue(objectMapper.writeValueAsString(event), ShipmentCreatedEvent.class);

        assertEquals("SHIP1", deserialized.getShipmentId());
        assertEquals("TRK-ABC123", deserialized.getTrackingNumber());
        assertEquals("ORDER1", deserialized.getOrderId());
        assertEquals(ShipmentCreatedEvent.EVENT_TYPE, "ShipmentCreated");
        assertEquals(ShipmentCreatedEvent.TOPIC, "shipping-events");
    }

    @Test
    void testShipmentDeliveredEventSerialization() throws Exception {
        ShipmentDeliveredEvent event = ShipmentDeliveredEvent.builder()
                .shipmentId("SHIP1")
                .orderId("ORDER1")
                .trackingNumber("TRK-ABC123")
                .deliveredAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        ShipmentDeliveredEvent deserialized =
                objectMapper.readValue(objectMapper.writeValueAsString(event), ShipmentDeliveredEvent.class);

        assertEquals("SHIP1", deserialized.getShipmentId());
        assertEquals("TRK-ABC123", deserialized.getTrackingNumber());
        assertEquals(ShipmentDeliveredEvent.EVENT_TYPE, "ShipmentDelivered");
    }

    @Test
    void testNotificationSentEventSerialization() throws Exception {
        NotificationSentEvent event = NotificationSentEvent.builder()
                .notificationId("NOTIF1")
                .orderId("ORDER1")
                .type("SHIPPED")
                .channel("EMAIL")
                .recipient("customer-1@example.com")
                .status("SENT")
                .sentAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        NotificationSentEvent deserialized =
                objectMapper.readValue(objectMapper.writeValueAsString(event), NotificationSentEvent.class);

        assertEquals("NOTIF1", deserialized.getNotificationId());
        assertEquals("SHIPPED", deserialized.getType());
        assertEquals("EMAIL", deserialized.getChannel());
        assertEquals("customer-1@example.com", deserialized.getRecipient());
        assertEquals(NotificationSentEvent.TOPIC, "notification-events");
    }
}
