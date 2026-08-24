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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Projects order/payment/shipping events into analytics facts and rollups
 * (Phase 10).
 *
 * Idempotency strategy: events first land in per-order FACT tables guarded by
 * unique constraints (INSERT ... ON CONFLICT DO NOTHING / DO UPDATE), then the
 * affected daily/product rollups are RECOMPUTED from those facts. Recomputation
 * converges to the correct numbers no matter how often Kafka redelivers an
 * event or in what order events arrive — incremental counters could not offer
 * that guarantee under at-least-once delivery.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsAggregationService {

    private final OrderFactRepository orderFactRepository;
    private final OrderItemFactRepository orderItemFactRepository;
    private final OrderRevenueRepository orderRevenueRepository;
    private final FulfillmentMetricRepository fulfillmentMetricRepository;
    private final DailyMetricRepository dailyMetricRepository;
    private final ProductMetricRepository productMetricRepository;

    @Transactional
    public void recordOrderCreated(OrderCreatedEvent event) {
        Long orderId = Long.valueOf(event.getOrderId());
        Long customerId = parseId(event.getCustomerId());
        LocalDateTime orderedAt = event.getCreatedAt() != null
                ? event.getCreatedAt()
                : LocalDateTime.now();
        LocalDate orderedDate = orderedAt.toLocalDate();
        List<OrderCreatedEvent.OrderItem> items = event.getOrderItems() == null
                ? List.of()
                : event.getOrderItems();

        orderFactRepository.upsert(orderId, customerId, toBigDecimal(event.getTotalAmount()),
                orderedDate, orderedAt);
        for (OrderCreatedEvent.OrderItem item : items) {
            orderItemFactRepository.upsert(orderId, Long.valueOf(item.getProductId()),
                    item.getQuantity() == null ? 0 : item.getQuantity(),
                    toBigDecimal(item.getUnitPrice()), orderedDate);
        }

        fulfillmentMetricRepository.upsertOrderedAt(orderId, orderedAt);
        fulfillmentMetricRepository.recomputeDurations(orderId);

        dailyMetricRepository.recomputeForDate(orderedDate);
        items.stream()
                .map(item -> Long.valueOf(item.getProductId()))
                .distinct()
                .forEach(productId -> productMetricRepository.recompute(orderedDate, productId));

        log.info("Analytics: recorded OrderCreated orderId={} date={} items={}",
                orderId, orderedDate, items.size());
    }

    @Transactional
    public void recordPaymentProcessed(PaymentProcessedEvent event) {
        if (event.getStatus() == null || event.getStatus() == PaymentProcessedEvent.PaymentStatus.PENDING) {
            log.info("Analytics: skipping payment event orderId={} status={}",
                    event.getOrderId(), event.getStatus());
            return;
        }

        Long orderId = Long.valueOf(event.getOrderId());
        LocalDateTime paidAt = event.getCreatedAt() != null
                ? event.getCreatedAt()
                : LocalDateTime.now();

        orderRevenueRepository.upsert(orderId, parseId(event.getCustomerId()),
                toBigDecimal(event.getAmount()), event.getStatus().name(),
                event.getTransactionId(), paidAt);

        // Daily metrics attribute to the ORDER date; if the order event has not
        // been seen yet the row is still anchored and will be picked up when the
        // order's own recompute runs.
        orderFactRepository.findOrderedDateByOrderId(orderId).ifPresentOrElse(
                dailyMetricRepository::recomputeForDate,
                () -> log.warn("Analytics: payment for unknown order {}; daily metrics not recomputed",
                        orderId));

        log.info("Analytics: recorded PaymentProcessed orderId={} status={}", orderId, event.getStatus());
    }

    @Transactional
    public void recordShipmentCreated(ShipmentCreatedEvent event) {
        Long orderId = Long.valueOf(event.getOrderId());
        LocalDateTime shippedAt = event.getShippedAt() != null
                ? event.getShippedAt()
                : event.getCreatedAt();

        fulfillmentMetricRepository.upsertShippedAt(orderId, shippedAt);
        fulfillmentMetricRepository.recomputeDurations(orderId);

        log.info("Analytics: recorded ShipmentCreated orderId={}", orderId);
    }

    @Transactional
    public void recordShipmentDelivered(ShipmentDeliveredEvent event) {
        Long orderId = Long.valueOf(event.getOrderId());
        LocalDateTime deliveredAt = event.getDeliveredAt() != null
                ? event.getDeliveredAt()
                : event.getCreatedAt();

        fulfillmentMetricRepository.upsertDeliveredAt(orderId, deliveredAt);
        fulfillmentMetricRepository.recomputeDurations(orderId);

        log.info("Analytics: recorded ShipmentDelivered orderId={}", orderId);
    }

    /**
     * Reconciliation sweep: recomputes rollups for every date with order facts
     * inside the lookback window. The per-event immediate recompute is correct
     * for sequentially committed events, but two consumer transactions racing
     * on different topics can each miss the other's uncommitted writes and
     * leave the last-written rollup stale. The sweep is fully idempotent, so
     * running it repeatedly converges the rollups to the truth.
     */
    @Transactional
    public void reconcileRecentMetrics(LocalDate today, int lookbackDays) {
        LocalDate since = today.minusDays(lookbackDays);
        for (LocalDate date : orderFactRepository.findDistinctOrderedDatesSince(since)) {
            dailyMetricRepository.recomputeForDate(date);
            for (Long productId : orderItemFactRepository.findDistinctProductIdsByOrderedDate(date)) {
                productMetricRepository.recompute(date, productId);
            }
        }
    }

    private Long parseId(String id) {
        return id == null ? null : Long.valueOf(id);
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }
}
