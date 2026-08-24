package com.enterprise.order.analytics.integration;

import com.enterprise.order.analytics.entity.DailyMetric;
import com.enterprise.order.analytics.entity.FulfillmentMetric;
import com.enterprise.order.analytics.entity.OrderRevenue;
import com.enterprise.order.analytics.entity.ProductMetric;
import com.enterprise.order.analytics.repository.DailyMetricRepository;
import com.enterprise.order.analytics.repository.FulfillmentMetricRepository;
import com.enterprise.order.analytics.repository.OrderRevenueRepository;
import com.enterprise.order.analytics.repository.ProductMetricRepository;
import com.enterprise.order.shared.events.OrderCreatedEvent;
import com.enterprise.order.shared.events.PaymentProcessedEvent;
import com.enterprise.order.shared.events.ShipmentCreatedEvent;
import com.enterprise.order.shared.events.ShipmentDeliveredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Full consumer path: publish real JSON events to an embedded Kafka broker and
 * assert the analytics projections land in PostgreSQL. Each test owns a
 * distinct metric date / order id so the shared schema stays isolated.
 */
// Close the context after this class so the reconciliation scheduler and the
// Hikari pool shut down cleanly instead of firing once more at JVM exit.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        // Fast reconciliation sweep: some tests publish events back-to-back, and
        // the sweep guarantees the rollups converge even if the two consumer
        // transactions raced each other (see MetricsAggregationService). The wide
        // lookback covers the fixed 2026-08 test dates used below.
        "analytics.reconcile.interval-ms=2000",
        "analytics.reconcile.lookback-days=3650"
})
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(partitions = 1,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers",
        topics = {"order-events", "payment-events", "shipping-events"})
class AnalyticsConsumerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("enterprise_order_analytics_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Mirror the Docker Compose URL (?currentSchema=analytics) so unqualified
        // table names resolve in the service schema for Hibernate's ddl-auto: validate.
        registry.add("spring.datasource.url", () -> {
            String url = postgres.getJdbcUrl();
            return url + (url.contains("?") ? "&" : "?") + "currentSchema=analytics";
        });
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DailyMetricRepository dailyMetricRepository;
    @Autowired
    private ProductMetricRepository productMetricRepository;
    @Autowired
    private OrderRevenueRepository orderRevenueRepository;
    @Autowired
    private FulfillmentMetricRepository fulfillmentMetricRepository;

    @Test
    void orderCreated_projectsDailyAndProductMetrics() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 1);

        publishOrder(5001L, 601L, 300.0,
                LocalDateTime.of(date, java.time.LocalTime.of(10, 15)),
                List.of(item(7001L, 2, 100.0), item(7002L, 1, 100.0)));

        awaitUntil(() -> dailyMetricRepository.findByMetricDate(date)
                .map(m -> m.getTotalOrders() == 1L).orElse(false),
                "daily metrics for order 5001");

        DailyMetric daily = dailyMetricRepository.findByMetricDate(date).orElseThrow();
        assertEquals(1L, daily.getTotalOrders());
        assertEquals(1L, daily.getDistinctCustomers());
        assertEquals(0, BigDecimal.ZERO.compareTo(daily.getTotalRevenue()));
        assertEquals(0L, daily.getCompletedOrders());

        awaitUntil(() -> productMetricRepository.findByMetricDateAndProductId(date, 7001L).isPresent(),
                "product metrics for product 7001");
        ProductMetric p1 = productMetricRepository.findByMetricDateAndProductId(date, 7001L).orElseThrow();
        assertEquals(2L, p1.getUnitsSold());
        assertEquals(0, new BigDecimal("200.00").compareTo(p1.getRevenue()));
        assertEquals(1L, p1.getTimesInOrder());

        ProductMetric p2 = productMetricRepository.findByMetricDateAndProductId(date, 7002L).orElseThrow();
        assertEquals(1L, p2.getUnitsSold());
        assertEquals(0, new BigDecimal("100.00").compareTo(p2.getRevenue()));
    }

    @Test
    void redeliveredOrderCreated_doesNotDoubleCount() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 2);
        OrderCreatedEvent event = orderEvent(5002L, 602L, 120.0,
                LocalDateTime.of(date, java.time.LocalTime.of(9, 0)),
                List.of(item(7003L, 1, 120.0)));

        // At-least-once delivery: the identical message arrives twice.
        send("order-events", event.getOrderId(), objectMapper.writeValueAsString(event), null);
        send("order-events", event.getOrderId(), objectMapper.writeValueAsString(event), null);

        awaitUntil(() -> dailyMetricRepository.findByMetricDate(date).isPresent(),
                "daily metrics for redelivered order 5002");

        DailyMetric daily = dailyMetricRepository.findByMetricDate(date).orElseThrow();
        assertEquals(1L, daily.getTotalOrders());
        assertEquals(1L, daily.getDistinctCustomers());

        ProductMetric product = productMetricRepository.findByMetricDateAndProductId(date, 7003L).orElseThrow();
        assertEquals(1L, product.getUnitsSold());
        assertEquals(1L, product.getTimesInOrder());
    }

    @Test
    void paymentCompleted_updatesRevenueAndCompletedCount() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 3);
        LocalDateTime base = LocalDateTime.of(date, java.time.LocalTime.of(11, 30));

        publishOrder(5003L, 603L, 150.0, base, List.of(item(7004L, 1, 150.0)));
        publishPayment(5003L, 603L, 150.0, PaymentProcessedEvent.PaymentStatus.COMPLETED,
                "TXN-5003", base.plusMinutes(1));

        awaitUntil(() -> dailyMetricRepository.findByMetricDate(date)
                .map(m -> m.getCompletedOrders() == 1L).orElse(false),
                "completed payment for order 5003");

        DailyMetric daily = dailyMetricRepository.findByMetricDate(date).orElseThrow();
        assertEquals(0, new BigDecimal("150.00").compareTo(daily.getTotalRevenue()));
        assertEquals(0, new BigDecimal("150.00").compareTo(daily.getAvgOrderValue()));

        Optional<OrderRevenue> revenue = orderRevenueRepository.findAll().stream()
                .filter(r -> r.getOrderId().equals(5003L)).findFirst();
        assertTrue(revenue.isPresent());
        assertEquals("COMPLETED", revenue.get().getPaymentStatus());
        assertEquals("TXN-5003", revenue.get().getTransactionId());
    }

    @Test
    void paymentFailed_countsFailedOrderWithoutRevenue() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 4);
        LocalDateTime base = LocalDateTime.of(date, java.time.LocalTime.of(12, 0));

        publishOrder(5004L, 604L, 80.0, base, List.of(item(7005L, 1, 80.0)));
        publishPayment(5004L, 604L, 80.0, PaymentProcessedEvent.PaymentStatus.FAILED,
                null, base.plusMinutes(1));

        awaitUntil(() -> dailyMetricRepository.findByMetricDate(date)
                .map(m -> m.getFailedOrders() == 1L).orElse(false),
                "failed payment for order 5004");

        DailyMetric daily = dailyMetricRepository.findByMetricDate(date).orElseThrow();
        assertEquals(1L, daily.getFailedOrders());
        assertEquals(0L, daily.getCompletedOrders());
        assertEquals(0, BigDecimal.ZERO.compareTo(daily.getTotalRevenue()));
    }

    @Test
    void refundAfterCompletion_removesRevenue() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 5);
        LocalDateTime base = LocalDateTime.of(date, java.time.LocalTime.of(13, 0));

        publishOrder(5005L, 605L, 90.0, base, List.of(item(7006L, 1, 90.0)));
        publishPayment(5005L, 605L, 90.0, PaymentProcessedEvent.PaymentStatus.COMPLETED,
                "TXN-5005", base.plusMinutes(1));

        awaitUntil(() -> dailyMetricRepository.findByMetricDate(date)
                .map(m -> m.getCompletedOrders() == 1L).orElse(false),
                "completed payment for order 5005");

        publishPayment(5005L, 605L, 90.0, PaymentProcessedEvent.PaymentStatus.REFUNDED,
                "TXN-5005", base.plusMinutes(30));

        awaitUntil(() -> dailyMetricRepository.findByMetricDate(date)
                .map(m -> BigDecimal.ZERO.compareTo(m.getTotalRevenue()) == 0).orElse(false),
                "refund of order 5005");

        DailyMetric daily = dailyMetricRepository.findByMetricDate(date).orElseThrow();
        assertEquals(0L, daily.getCompletedOrders());
        assertEquals("REFUNDED", orderRevenueRepository.findAll().stream()
                .filter(r -> r.getOrderId().equals(5005L)).findFirst().orElseThrow().getPaymentStatus());
    }

    @Test
    void shippingEvents_populateFulfillmentDurations() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 6);
        LocalDateTime orderedAt = LocalDateTime.of(date, java.time.LocalTime.of(10, 0));

        publishOrder(5006L, 606L, 60.0, orderedAt, List.of(item(7007L, 1, 60.0)));

        ShipmentCreatedEvent created = ShipmentCreatedEvent.builder()
                .shipmentId("9001").orderId("5006").customerId("606")
                .trackingNumber("TRK-5006")
                .shippedAt(orderedAt.plusMinutes(1))
                .createdAt(orderedAt.plusMinutes(1))
                .build();
        send("shipping-events", "5006", objectMapper.writeValueAsString(created),
                ShipmentCreatedEvent.EVENT_TYPE);

        ShipmentDeliveredEvent delivered = ShipmentDeliveredEvent.builder()
                .shipmentId("9001").orderId("5006").customerId("606")
                .trackingNumber("TRK-5006")
                .deliveredAt(orderedAt.plusMinutes(5))
                .createdAt(orderedAt.plusMinutes(5))
                .build();
        send("shipping-events", "5006", objectMapper.writeValueAsString(delivered),
                ShipmentDeliveredEvent.EVENT_TYPE);

        awaitUntil(() -> fulfillmentMetricRepository.findAll().stream()
                        .filter(f -> f.getOrderId().equals(5006L))
                        .findFirst()
                        .map(f -> f.getOrderToShipSeconds() != null && f.getOrderToDeliverSeconds() != null)
                        .orElse(false),
                "fulfillment durations for order 5006");

        FulfillmentMetric metric = fulfillmentMetricRepository.findAll().stream()
                .filter(f -> f.getOrderId().equals(5006L)).findFirst().orElseThrow();
        assertEquals(60L, metric.getOrderToShipSeconds());
        assertEquals(300L, metric.getOrderToDeliverSeconds());
    }

    // --- helpers -------------------------------------------------------------

    private void publishOrder(Long orderId, Long customerId, Double totalAmount,
                              LocalDateTime createdAt, List<OrderCreatedEvent.OrderItem> items) throws Exception {
        OrderCreatedEvent event = orderEvent(orderId, customerId, totalAmount, createdAt, items);
        send("order-events", event.getOrderId(), objectMapper.writeValueAsString(event), null);
    }

    private void publishPayment(Long orderId, Long customerId, Double amount,
                                PaymentProcessedEvent.PaymentStatus status, String transactionId,
                                LocalDateTime createdAt) throws Exception {
        PaymentProcessedEvent event = PaymentProcessedEvent.builder()
                .paymentId("PAY-" + orderId)
                .orderId(orderId.toString())
                .customerId(customerId.toString())
                .amount(amount)
                .status(status)
                .transactionId(transactionId)
                .createdAt(createdAt)
                .build();
        send("payment-events", event.getOrderId(), objectMapper.writeValueAsString(event), null);
    }

    private OrderCreatedEvent orderEvent(Long orderId, Long customerId, Double totalAmount,
                                         LocalDateTime createdAt, List<OrderCreatedEvent.OrderItem> items) {
        return OrderCreatedEvent.builder()
                .orderId(orderId.toString())
                .orderNumber("ORD-" + orderId)
                .customerId(customerId.toString())
                .totalAmount(totalAmount)
                .orderItems(items)
                .createdAt(createdAt)
                .build();
    }

    private OrderCreatedEvent.OrderItem item(Long productId, int quantity, double unitPrice) {
        return OrderCreatedEvent.OrderItem.builder()
                .productId(productId.toString())
                .quantity(quantity)
                .unitPrice(unitPrice)
                .build();
    }

    private void send(String topic, String key, String json, String eventType) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, key, json);
        if (eventType != null) {
            record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        }
        kafkaTemplate.send(record).get();
    }

    private void awaitUntil(BooleanSupplier condition, String description) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for: " + description);
            }
        }
        fail("Timed out waiting for: " + description);
    }
}
