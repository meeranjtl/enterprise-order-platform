package com.enterprise.order.analytics.messaging;

import com.enterprise.order.analytics.service.MetricsAggregationService;
import com.enterprise.order.shared.events.PaymentProcessedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes payment outcomes (COMPLETED / FAILED / REFUNDED) for revenue
 * metrics. PENDING events are ignored by the aggregation service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final ObjectMapper objectMapper;
    private final MetricsAggregationService metricsAggregationService;

    @KafkaListener(topics = "payment-events", groupId = "analytics-service-group")
    public void onPaymentProcessed(ConsumerRecord<String, String> record) throws Exception {
        PaymentProcessedEvent event = objectMapper.readValue(record.value(), PaymentProcessedEvent.class);
        log.info("Analytics consumer: PaymentProcessedEvent orderId={} status={}",
                event.getOrderId(), event.getStatus());
        metricsAggregationService.recordPaymentProcessed(event);
    }
}
