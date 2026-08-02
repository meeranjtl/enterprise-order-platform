package com.enterprise.order.payment.messaging;

import com.enterprise.order.payment.dto.PaymentDTO;
import com.enterprise.order.payment.entity.PaymentStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "payment.kafka.enabled", havingValue = "true")
public class PaymentEventPublisher {

    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(PaymentDTO payment) {
        kafkaTemplate.send(
                PAYMENT_EVENTS_TOPIC,
                payment.getId().toString(),
                new PaymentEvent(eventType(payment.getStatus()), payment));
    }

    private String eventType(PaymentStatus status) {
        return switch (status) {
            case COMPLETED -> "PaymentCompleted";
            case FAILED -> "PaymentFailed";
            case REFUNDED -> "PaymentRefunded";
            default -> "PaymentInitiated";
        };
    }

    public record PaymentEvent(String type, PaymentDTO payment) {
    }
}
