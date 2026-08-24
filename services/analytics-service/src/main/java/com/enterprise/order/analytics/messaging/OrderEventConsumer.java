package com.enterprise.order.analytics.messaging;

import com.enterprise.order.analytics.service.MetricsAggregationService;
import com.enterprise.order.shared.events.OrderCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes order creation events for the analytics projections. Runs in its own
 * consumer group so it never disturbs the saga consumers. Exceptions propagate
 * to the shared error handler → DLQ after retries.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final ObjectMapper objectMapper;
    private final MetricsAggregationService metricsAggregationService;

    @KafkaListener(topics = "order-events", groupId = "analytics-service-group")
    public void onOrderCreated(ConsumerRecord<String, String> record) throws Exception {
        OrderCreatedEvent event = objectMapper.readValue(record.value(), OrderCreatedEvent.class);
        log.info("Analytics consumer: OrderCreatedEvent orderId={}", event.getOrderId());
        metricsAggregationService.recordOrderCreated(event);
    }
}
