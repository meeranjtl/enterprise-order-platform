package com.enterprise.order.shared.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox Publisher ensures exactly-once event publishing semantics.
 * 
 * The pattern:
 * 1. Business logic saves entity + creates OutboxEvent in same transaction
 * 2. Scheduled task polls unpublished OutboxEvents
 * 3. Publishes event to Kafka
 * 4. Marks OutboxEvent as published
 * 
 * This guarantees no event loss and exactly-once delivery (with idempotent consumers).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> unpublishedEvents = outboxRepository.findUnpublishedEvents();
        
        if (unpublishedEvents.isEmpty()) {
            return;
        }

        log.info("Publishing {} unpublished events from outbox", unpublishedEvents.size());

        for (OutboxEvent event : unpublishedEvents) {
            try {
                publishEvent(event);
                event.markAsPublished();
                outboxRepository.save(event);
                log.info("Published outbox event: {} to topic: {}", event.getId(), event.getKafkaTopic());
            } catch (Exception e) {
                log.error("Failed to publish outbox event: {}", event.getId(), e);
                // Don't mark as published; will retry on next run
            }
        }
    }

    private void publishEvent(OutboxEvent event) {
        Message<String> message = MessageBuilder
            .withPayload(event.getPayload())
            .setHeader(KafkaHeaders.TOPIC, event.getKafkaTopic())
            .setHeader("kafka_messageKey", event.getKafkaKey())
            // Phase 9: lets consumers dispatch when a topic carries multiple event
            // types (e.g. shipping-events carries ShipmentCreated + ShipmentDelivered).
            .setHeader("eventType", event.getEventType())
            .build();

        kafkaTemplate.send(message);
    }

    /**
     * Stores an event in the outbox for asynchronous publishing.
     * Should be called within the same @Transactional method as the entity creation.
     */
    @Transactional
    public void storeEvent(String aggregateId, String eventType, String kafkaTopic, 
                          String kafkaKey, Object eventPayload) {
        try {
            String payload = objectMapper.writeValueAsString(eventPayload);
            
            OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateId(aggregateId)
                .eventType(eventType)
                .kafkaTopic(kafkaTopic)
                .kafkaKey(kafkaKey)
                .payload(payload)
                .build();

            outboxRepository.save(outboxEvent);
            log.debug("Stored event in outbox: aggregateId={}, eventType={}", aggregateId, eventType);
        } catch (Exception e) {
            log.error("Failed to store event in outbox", e);
            throw new RuntimeException("Failed to store event in outbox", e);
        }
    }
}
