package com.enterprise.order.analytics.messaging;

import com.enterprise.order.analytics.service.MetricsAggregationService;
import com.enterprise.order.shared.events.OrderCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    private OrderEventConsumer consumer;
    private ObjectMapper objectMapper;

    @Mock
    private MetricsAggregationService metricsAggregationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        consumer = new OrderEventConsumer(objectMapper, metricsAggregationService);
    }

    @Test
    void validPayload_isParsedAndDelegated() throws Exception {
        String json = """
                {"orderId":"101","orderNumber":"ORD-101","customerId":"7","totalAmount":250.0,
                 "orderItems":[{"productId":"11","quantity":2,"unitPrice":125.0}],
                 "createdAt":"2026-08-22T10:15:30"}""";

        consumer.onOrderCreated(new ConsumerRecord<>("order-events", 0, 0, "101", json));

        ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(metricsAggregationService).recordOrderCreated(captor.capture());
        assertEquals("101", captor.getValue().getOrderId());
        assertEquals(1, captor.getValue().getOrderItems().size());
    }

    @Test
    void malformedPayload_propagatesToErrorHandlerForDlq() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("order-events", 0, 0, "101", "{not-json");

        assertThrows(Exception.class, () -> consumer.onOrderCreated(record));
    }
}
