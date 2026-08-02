package com.enterprise.order.shared.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeadLetterQueueHandler {

    @KafkaListener(topics = "order-events-dlq", groupId = "order-events-dlq-group")
    public void handleOrderEventsDLQ(ConsumerRecord<String, String> record) {
        log.error("DLQ: order-events message failed. Key: {}, Value: {}, Topic: {}, Partition: {}, Offset: {}",
                record.key(), record.value(), record.topic(), record.partition(), record.offset());
    }

    @KafkaListener(topics = "inventory-events-dlq", groupId = "inventory-events-dlq-group")
    public void handleInventoryEventsDLQ(ConsumerRecord<String, String> record) {
        log.error("DLQ: inventory-events message failed. Key: {}, Value: {}, Topic: {}, Partition: {}, Offset: {}",
                record.key(), record.value(), record.topic(), record.partition(), record.offset());
    }

    @KafkaListener(topics = "payment-events-dlq", groupId = "payment-events-dlq-group")
    public void handlePaymentEventsDLQ(ConsumerRecord<String, String> record) {
        log.error("DLQ: payment-events message failed. Key: {}, Value: {}, Topic: {}, Partition: {}, Offset: {}",
                record.key(), record.value(), record.topic(), record.partition(), record.offset());
    }
}
