package com.enterprise.order.analytics.service;

import com.enterprise.order.analytics.repository.DailyMetricRepository;
import com.enterprise.order.analytics.repository.FulfillmentMetricRepository;
import com.enterprise.order.analytics.repository.OrderFactRepository;
import com.enterprise.order.analytics.repository.OrderItemFactRepository;
import com.enterprise.order.analytics.repository.OrderRevenueRepository;
import com.enterprise.order.analytics.repository.ProductMetricRepository;
import com.enterprise.order.shared.events.OrderCreatedEvent;
import com.enterprise.order.shared.events.PaymentProcessedEvent;
import com.enterprise.order.shared.events.ShipmentCreatedEvent;
import com.enterprise.order.shared.events.ShipmentDeliveredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsAggregationServiceTest {

    private static final LocalDateTime ORDERED_AT = LocalDateTime.of(2026, 8, 22, 10, 15, 30);
    private static final LocalDate ORDERED_DATE = ORDERED_AT.toLocalDate();

    @Mock
    private OrderFactRepository orderFactRepository;
    @Mock
    private OrderItemFactRepository orderItemFactRepository;
    @Mock
    private OrderRevenueRepository orderRevenueRepository;
    @Mock
    private FulfillmentMetricRepository fulfillmentMetricRepository;
    @Mock
    private DailyMetricRepository dailyMetricRepository;
    @Mock
    private ProductMetricRepository productMetricRepository;

    @InjectMocks
    private MetricsAggregationService service;

    @Test
    void recordOrderCreated_storesFactsAndRecomputesRollups() {
        OrderCreatedEvent event = orderEvent(101L, 7L, 250.0,
                List.of(item(11L, 2, 100.0), item(12L, 1, 50.0)));

        service.recordOrderCreated(event);

        verify(orderFactRepository).upsert(101L, 7L, BigDecimal.valueOf(250.0), ORDERED_DATE, ORDERED_AT);
        verify(orderItemFactRepository).upsert(101L, 11L, 2, BigDecimal.valueOf(100.0), ORDERED_DATE);
        verify(orderItemFactRepository).upsert(101L, 12L, 1, BigDecimal.valueOf(50.0), ORDERED_DATE);
        verify(fulfillmentMetricRepository).upsertOrderedAt(101L, ORDERED_AT);
        verify(fulfillmentMetricRepository).recomputeDurations(101L);
        verify(dailyMetricRepository).recomputeForDate(ORDERED_DATE);
        verify(productMetricRepository).recompute(ORDERED_DATE, 11L);
        verify(productMetricRepository).recompute(ORDERED_DATE, 12L);
    }

    @Test
    void recordOrderCreated_duplicateProductRecomputedOnce() {
        OrderCreatedEvent event = orderEvent(101L, 7L, 200.0,
                List.of(item(11L, 1, 100.0), item(11L, 1, 100.0)));

        service.recordOrderCreated(event);

        verify(productMetricRepository).recompute(ORDERED_DATE, 11L);
    }

    @Test
    void recordOrderCreated_withoutItemsStillRecomputesDaily() {
        OrderCreatedEvent event = orderEvent(101L, 7L, 0.0, null);

        service.recordOrderCreated(event);

        verify(orderFactRepository).upsert(101L, 7L, BigDecimal.valueOf(0.0), ORDERED_DATE, ORDERED_AT);
        verifyNoInteractions(orderItemFactRepository);
        verifyNoInteractions(productMetricRepository);
        verify(dailyMetricRepository).recomputeForDate(ORDERED_DATE);
    }

    @Test
    void recordPaymentProcessed_completedUpdatesRevenueAndDaily() {
        when(orderFactRepository.findOrderedDateByOrderId(101L)).thenReturn(Optional.of(ORDERED_DATE));

        service.recordPaymentProcessed(paymentEvent(101L, 7L, 250.0,
                PaymentProcessedEvent.PaymentStatus.COMPLETED, "TXN-1"));

        verify(orderRevenueRepository).upsert(101L, 7L, BigDecimal.valueOf(250.0),
                "COMPLETED", "TXN-1", ORDERED_AT);
        verify(dailyMetricRepository).recomputeForDate(ORDERED_DATE);
    }

    @Test
    void recordPaymentProcessed_failedRecordedForDailyCounts() {
        when(orderFactRepository.findOrderedDateByOrderId(101L)).thenReturn(Optional.of(ORDERED_DATE));

        service.recordPaymentProcessed(paymentEvent(101L, 7L, 250.0,
                PaymentProcessedEvent.PaymentStatus.FAILED, null));

        verify(orderRevenueRepository).upsert(101L, 7L, BigDecimal.valueOf(250.0),
                "FAILED", null, ORDERED_AT);
        verify(dailyMetricRepository).recomputeForDate(ORDERED_DATE);
    }

    @Test
    void recordPaymentProcessed_refundedOverwritesOutcome() {
        when(orderFactRepository.findOrderedDateByOrderId(101L)).thenReturn(Optional.of(ORDERED_DATE));

        service.recordPaymentProcessed(paymentEvent(101L, 7L, 250.0,
                PaymentProcessedEvent.PaymentStatus.REFUNDED, "TXN-1"));

        verify(orderRevenueRepository).upsert(101L, 7L, BigDecimal.valueOf(250.0),
                "REFUNDED", "TXN-1", ORDERED_AT);
        verify(dailyMetricRepository).recomputeForDate(ORDERED_DATE);
    }

    @Test
    void recordPaymentProcessed_pendingIgnored() {
        service.recordPaymentProcessed(paymentEvent(101L, 7L, 250.0,
                PaymentProcessedEvent.PaymentStatus.PENDING, null));

        verifyNoInteractions(orderRevenueRepository, dailyMetricRepository, orderFactRepository);
    }

    @Test
    void recordPaymentProcessed_unknownOrderAnchoredWithoutDailyRecompute() {
        when(orderFactRepository.findOrderedDateByOrderId(999L)).thenReturn(Optional.empty());

        service.recordPaymentProcessed(paymentEvent(999L, 7L, 100.0,
                PaymentProcessedEvent.PaymentStatus.COMPLETED, "TXN-9"));

        verify(orderRevenueRepository).upsert(eq(999L), eq(7L), any(), eq("COMPLETED"), eq("TXN-9"), any());
        verify(dailyMetricRepository, never()).recomputeForDate(any());
    }

    @Test
    void reconcileRecentMetrics_recomputesAllDatesAndProductsInWindow() {
        LocalDate today = ORDERED_DATE;
        LocalDate yesterday = today.minusDays(1);
        when(orderFactRepository.findDistinctOrderedDatesSince(today.minusDays(2)))
                .thenReturn(List.of(yesterday, today));
        when(orderItemFactRepository.findDistinctProductIdsByOrderedDate(yesterday))
                .thenReturn(List.of(11L));
        when(orderItemFactRepository.findDistinctProductIdsByOrderedDate(today))
                .thenReturn(List.of(11L, 12L));

        service.reconcileRecentMetrics(today, 2);

        verify(dailyMetricRepository).recomputeForDate(yesterday);
        verify(dailyMetricRepository).recomputeForDate(today);
        verify(productMetricRepository).recompute(yesterday, 11L);
        verify(productMetricRepository).recompute(today, 11L);
        verify(productMetricRepository).recompute(today, 12L);
    }

    @Test
    void reconcileRecentMetrics_noFactsDoesNothing() {
        when(orderFactRepository.findDistinctOrderedDatesSince(any())).thenReturn(List.of());

        service.reconcileRecentMetrics(ORDERED_DATE, 3);

        verifyNoInteractions(dailyMetricRepository, productMetricRepository);
    }

    @Test
    void recordShipmentCreated_updatesFulfillmentAndDurations() {
        LocalDateTime shippedAt = ORDERED_AT.plusMinutes(20);

        service.recordShipmentCreated(ShipmentCreatedEvent.builder()
                .orderId("101")
                .shippedAt(shippedAt)
                .build());

        verify(fulfillmentMetricRepository).upsertShippedAt(101L, shippedAt);
        verify(fulfillmentMetricRepository).recomputeDurations(101L);
    }

    @Test
    void recordShipmentDelivered_updatesFulfillmentAndDurations() {
        LocalDateTime deliveredAt = ORDERED_AT.plusHours(5);

        service.recordShipmentDelivered(ShipmentDeliveredEvent.builder()
                .orderId("101")
                .deliveredAt(deliveredAt)
                .build());

        verify(fulfillmentMetricRepository).upsertDeliveredAt(101L, deliveredAt);
        verify(fulfillmentMetricRepository).recomputeDurations(101L);
    }

    private OrderCreatedEvent orderEvent(Long orderId, Long customerId, Double total,
                                         List<OrderCreatedEvent.OrderItem> items) {
        return OrderCreatedEvent.builder()
                .orderId(orderId.toString())
                .orderNumber("ORD-" + orderId)
                .customerId(customerId == null ? null : customerId.toString())
                .totalAmount(total)
                .orderItems(items)
                .createdAt(ORDERED_AT)
                .build();
    }

    private OrderCreatedEvent.OrderItem item(Long productId, int quantity, double unitPrice) {
        return OrderCreatedEvent.OrderItem.builder()
                .productId(productId.toString())
                .quantity(quantity)
                .unitPrice(unitPrice)
                .build();
    }

    private PaymentProcessedEvent paymentEvent(Long orderId, Long customerId, Double amount,
                                               PaymentProcessedEvent.PaymentStatus status, String txn) {
        return PaymentProcessedEvent.builder()
                .paymentId("PAY-" + orderId)
                .orderId(orderId.toString())
                .customerId(customerId == null ? null : customerId.toString())
                .amount(amount)
                .status(status)
                .transactionId(txn)
                .createdAt(ORDERED_AT)
                .build();
    }
}
