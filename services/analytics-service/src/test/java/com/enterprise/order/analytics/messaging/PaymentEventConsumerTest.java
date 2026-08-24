package com.enterprise.order.analytics.messaging;

import com.enterprise.order.analytics.service.MetricsAggregationService;
import com.enterprise.order.shared.events.PaymentProcessedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    private PaymentEventConsumer consumer;
    private ObjectMapper objectMapper;

    @Mock
    private MetricsAggregationService metricsAggregationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new PaymentEventConsumer(objectMapper, metricsAggregationService);
    }

    @Test
    void validPayload_isParsedAndDelegated() throws Exception {
        String json = """
                {"paymentId":"PAY-1","orderId":"101","customerId":"7","amount":250.0,
                 "status":"COMPLETED","transactionId":"TXN-1","createdAt":"2026-08-22T10:16:00"}""";

        consumer.onPaymentProcessed(new ConsumerRecord<>("payment-events", 0, 0, "101", json));

        ArgumentCaptor<PaymentProcessedEvent> captor = ArgumentCaptor.forClass(PaymentProcessedEvent.class);
        verify(metricsAggregationService).recordPaymentProcessed(captor.capture());
        assertEquals(PaymentProcessedEvent.PaymentStatus.COMPLETED, captor.getValue().getStatus());
        assertEquals("TXN-1", captor.getValue().getTransactionId());
    }
}
