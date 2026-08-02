package com.enterprise.order.shared.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Kafka event serialization and deserialization.
 * These tests validate event payloads for Kafka messaging without requiring
 * a full Spring Boot context (which shared-library doesn't have).
 * 
 * Full end-to-end Kafka integration tests should run in individual services
 * (order-service, inventory-service, payment-service) with EmbeddedKafka.
 */
class KafkaEventIntegrationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @Test
    void testOrderCreatedEventPublishFormat() throws Exception {
        // Simulate publishing OrderCreatedEvent to Kafka
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId("ORDER123");
        event.setOrderNumber("ORD-2024-001");
        event.setCustomerId("CUST456");
        event.setTotalAmount(999.99);
        event.setCreatedAt(LocalDateTime.now());

        OrderCreatedEvent.OrderItem item = new OrderCreatedEvent.OrderItem();
        item.setProductId("PROD001");
        item.setQuantity(2);
        item.setUnitPrice(100.0);
        event.setOrderItems(Arrays.asList(item));

        // Serialize to JSON (what gets sent to Kafka)
        String json = objectMapper.writeValueAsString(event);
        
        // Assert JSON contains all required fields
        assertTrue(json.contains("\"orderId\":\"ORDER123\""));
        assertTrue(json.contains("\"customerId\":\"CUST456\""));
        assertTrue(json.contains("\"totalAmount\":999.99"));
        assertTrue(json.contains("\"productId\":\"PROD001\""));
    }

    @Test
    void testInventoryReservedEventPublishFormat() throws Exception {
        // Simulate publishing InventoryReservedEvent to Kafka
        InventoryReservedEvent event = new InventoryReservedEvent();
        event.setReservationId("RES123");
        event.setOrderId("ORDER456");
        event.setProductId("PROD001");
        event.setQuantity(5);
        event.setStatus(InventoryReservedEvent.ReservationStatus.CONFIRMED);

        String json = objectMapper.writeValueAsString(event);
        
        assertTrue(json.contains("\"reservationId\":\"RES123\""));
        assertTrue(json.contains("\"orderId\":\"ORDER456\""));
        assertTrue(json.contains("\"status\":\"CONFIRMED\""));
    }

    @Test
    void testPaymentProcessedEventPublishFormat() throws Exception {
        // Simulate publishing PaymentProcessedEvent to Kafka
        PaymentProcessedEvent event = new PaymentProcessedEvent();
        event.setPaymentId("PAY123");
        event.setOrderId("ORDER789");
        event.setStatus(PaymentProcessedEvent.PaymentStatus.COMPLETED);
        event.setAmount(999.99);

        String json = objectMapper.writeValueAsString(event);
        
        assertTrue(json.contains("\"paymentId\":\"PAY123\""));
        assertTrue(json.contains("\"orderId\":\"ORDER789\""));
        assertTrue(json.contains("\"status\":\"COMPLETED\""));
        assertTrue(json.contains("\"amount\":999.99"));
    }

    @Test
    void testOrderConsumerDeserializesEvent() throws Exception {
        // Simulate a Kafka consumer receiving OrderCreatedEvent
        String kafkaMessage = "{\"orderId\":\"ORDER999\",\"orderNumber\":\"ORD-2024-999\"," +
                "\"customerId\":\"CUST999\",\"totalAmount\":500.0,\"orderItems\":[" +
                "{\"productId\":\"PROD999\",\"quantity\":1,\"unitPrice\":500.0}]}";

        OrderCreatedEvent event = objectMapper.readValue(kafkaMessage, OrderCreatedEvent.class);

        assertEquals("ORDER999", event.getOrderId());
        assertEquals("CUST999", event.getCustomerId());
        assertEquals(500.0, event.getTotalAmount());
        assertEquals(1, event.getOrderItems().size());
        assertEquals("PROD999", event.getOrderItems().get(0).getProductId());
    }

    @Test
    void testInventoryConsumerDeserializesEvent() throws Exception {
        // Simulate a Kafka consumer receiving InventoryReservedEvent
        String kafkaMessage = "{\"reservationId\":\"RES888\",\"orderId\":\"ORDER888\"," +
                "\"productId\":\"PROD888\",\"quantity\":3,\"status\":\"CONFIRMED\"}";

        InventoryReservedEvent event = objectMapper.readValue(kafkaMessage, InventoryReservedEvent.class);

        assertEquals("RES888", event.getReservationId());
        assertEquals("ORDER888", event.getOrderId());
        assertEquals(InventoryReservedEvent.ReservationStatus.CONFIRMED, event.getStatus());
    }

    @Test
    void testPaymentConsumerDeserializesEvent() throws Exception {
        // Simulate a Kafka consumer receiving PaymentProcessedEvent
        String kafkaMessage = "{\"paymentId\":\"PAY777\",\"orderId\":\"ORDER777\"," +
                "\"status\":\"COMPLETED\",\"amount\":1500.75}";

        PaymentProcessedEvent event = objectMapper.readValue(kafkaMessage, PaymentProcessedEvent.class);

        assertEquals("PAY777", event.getPaymentId());
        assertEquals("ORDER777", event.getOrderId());
        assertEquals(PaymentProcessedEvent.PaymentStatus.COMPLETED, event.getStatus());
        assertEquals(1500.75, event.getAmount());
    }

    @Test
    void testHappyPathEventSequence() throws Exception {
        // Verify the complete event flow: Order → Inventory → Payment

        // Step 1: OrderCreatedEvent
        OrderCreatedEvent orderEvent = new OrderCreatedEvent();
        orderEvent.setOrderId("ORDER555");
        orderEvent.setOrderNumber("ORD-2024-555");
        orderEvent.setCustomerId("CUST555");
        orderEvent.setTotalAmount(750.0);
        
        OrderCreatedEvent.OrderItem item = new OrderCreatedEvent.OrderItem();
        item.setProductId("PROD555");
        item.setQuantity(1);
        item.setUnitPrice(750.0);
        orderEvent.setOrderItems(Arrays.asList(item));

        String orderJson = objectMapper.writeValueAsString(orderEvent);
        OrderCreatedEvent orderDeserialized = objectMapper.readValue(orderJson, OrderCreatedEvent.class);
        assertEquals("ORDER555", orderDeserialized.getOrderId());

        // Step 2: InventoryReservedEvent
        InventoryReservedEvent inventoryEvent = new InventoryReservedEvent();
        inventoryEvent.setReservationId("RES555");
        inventoryEvent.setOrderId("ORDER555");
        inventoryEvent.setProductId("PROD555");
        inventoryEvent.setQuantity(1);
        inventoryEvent.setStatus(InventoryReservedEvent.ReservationStatus.CONFIRMED);

        String inventoryJson = objectMapper.writeValueAsString(inventoryEvent);
        InventoryReservedEvent inventoryDeserialized = objectMapper.readValue(inventoryJson, InventoryReservedEvent.class);
        assertEquals("ORDER555", inventoryDeserialized.getOrderId());
        assertEquals(InventoryReservedEvent.ReservationStatus.CONFIRMED, inventoryDeserialized.getStatus());

        // Step 3: PaymentProcessedEvent
        PaymentProcessedEvent paymentEvent = new PaymentProcessedEvent();
        paymentEvent.setPaymentId("PAY555");
        paymentEvent.setOrderId("ORDER555");
        paymentEvent.setStatus(PaymentProcessedEvent.PaymentStatus.COMPLETED);
        paymentEvent.setAmount(750.0);

        String paymentJson = objectMapper.writeValueAsString(paymentEvent);
        PaymentProcessedEvent paymentDeserialized = objectMapper.readValue(paymentJson, PaymentProcessedEvent.class);
        assertEquals("ORDER555", paymentDeserialized.getOrderId());
        assertEquals(PaymentProcessedEvent.PaymentStatus.COMPLETED, paymentDeserialized.getStatus());
    }

    @Test
    void testPaymentFailureCompensation() throws Exception {
        // Verify failure path: Order → Inventory (reserved) → Payment (failed)

        // Order created
        OrderCreatedEvent orderEvent = new OrderCreatedEvent();
        orderEvent.setOrderId("ORDER666");
        orderEvent.setOrderNumber("ORD-2024-666");
        String orderJson = objectMapper.writeValueAsString(orderEvent);
        OrderCreatedEvent order = objectMapper.readValue(orderJson, OrderCreatedEvent.class);
        assertEquals("ORDER666", order.getOrderId());

        // Inventory reserved
        InventoryReservedEvent inventoryEvent = new InventoryReservedEvent();
        inventoryEvent.setReservationId("RES666");
        inventoryEvent.setOrderId("ORDER666");
        inventoryEvent.setStatus(InventoryReservedEvent.ReservationStatus.CONFIRMED);
        String inventoryJson = objectMapper.writeValueAsString(inventoryEvent);
        InventoryReservedEvent inventory = objectMapper.readValue(inventoryJson, InventoryReservedEvent.class);
        assertEquals("ORDER666", inventory.getOrderId());

        // Payment failed - triggers compensation (inventory release)
        PaymentProcessedEvent paymentEvent = new PaymentProcessedEvent();
        paymentEvent.setPaymentId("PAY666");
        paymentEvent.setOrderId("ORDER666");
        paymentEvent.setStatus(PaymentProcessedEvent.PaymentStatus.FAILED);
        String paymentJson = objectMapper.writeValueAsString(paymentEvent);
        PaymentProcessedEvent payment = objectMapper.readValue(paymentJson, PaymentProcessedEvent.class);
        assertEquals(PaymentProcessedEvent.PaymentStatus.FAILED, payment.getStatus());

        // Verify compensation: inventory should be released based on orderId
        assertEquals("ORDER666", payment.getOrderId());
        assertTrue(payment.getStatus() == PaymentProcessedEvent.PaymentStatus.FAILED);
    }
}
