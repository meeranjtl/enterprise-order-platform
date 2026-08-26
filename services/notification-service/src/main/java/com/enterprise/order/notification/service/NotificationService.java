package com.enterprise.order.notification.service;

import com.enterprise.order.notification.dto.NotificationDTO;
import com.enterprise.order.notification.entity.Notification;
import com.enterprise.order.notification.entity.NotificationChannel;
import com.enterprise.order.notification.entity.NotificationStatus;
import com.enterprise.order.notification.entity.NotificationType;
import com.enterprise.order.notification.repository.NotificationRepository;
import com.enterprise.order.shared.events.NotificationSentEvent;
import com.enterprise.order.shared.events.OrderCreatedEvent;
import com.enterprise.order.shared.events.PaymentProcessedEvent;
import com.enterprise.order.shared.events.ShipmentCreatedEvent;
import com.enterprise.order.shared.events.ShipmentDeliveredEvent;
import com.enterprise.order.shared.exception.ResourceNotFoundException;
import com.enterprise.order.shared.outbox.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Event-to-notification mapping (Phase 9).
 *
 * <pre>
 * OrderCreated                  → ORDER_CONFIRMED   (EMAIL)
 * PaymentProcessed (COMPLETED)  → PAYMENT_RECEIVED  (EMAIL)
 * ShipmentCreated               → SHIPPED           (EMAIL + SMS)
 * ShipmentDelivered             → DELIVERED         (EMAIL + SMS)
 * </pre>
 *
 * Idempotent per (order, type, channel) — unique constraint plus pre-check — so
 * at-least-once Kafka redelivery never duplicates a customer notification.
 * Recipients are simulated from the customerId (events carry no contact data and
 * AGENTS.md forbids new inter-service HTTP during Phase 8+).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final SmsService smsService;
    private final OutboxPublisher outboxPublisher;

    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        Long orderId = Long.valueOf(event.getOrderId());
        String email = emailFor(event.getCustomerId());
        send(orderId, NotificationType.ORDER_CONFIRMED, NotificationChannel.EMAIL, email,
                "Order confirmed - " + event.getOrderNumber(),
                String.format("Your order %s has been confirmed. Total amount: %.2f. We will notify you when it ships.",
                        event.getOrderNumber(), event.getTotalAmount() == null ? 0.0 : event.getTotalAmount()));
    }

    @Transactional
    public void handlePaymentCompleted(PaymentProcessedEvent event) {
        Long orderId = Long.valueOf(event.getOrderId());
        String email = emailFor(event.getCustomerId());
        send(orderId, NotificationType.PAYMENT_RECEIVED, NotificationChannel.EMAIL, email,
                "Payment received for order " + orderId,
                String.format("We received your payment of %.2f for order %s (transaction %s).",
                        event.getAmount() == null ? 0.0 : event.getAmount(), orderId, event.getTransactionId()));
    }

    @Transactional
    public void handleShipmentCreated(ShipmentCreatedEvent event) {
        Long orderId = Long.valueOf(event.getOrderId());
        String message = String.format("Your order %s has shipped. Tracking number: %s",
                orderId, event.getTrackingNumber());
        send(orderId, NotificationType.SHIPPED, NotificationChannel.EMAIL, emailFor(event.getCustomerId()),
                "Your order " + orderId + " is on its way", message);
        send(orderId, NotificationType.SHIPPED, NotificationChannel.SMS, phoneFor(event.getCustomerId()),
                null, message);
    }

    @Transactional
    public void handleShipmentDelivered(ShipmentDeliveredEvent event) {
        Long orderId = Long.valueOf(event.getOrderId());
        String message = String.format("Your order %s was delivered. Tracking number: %s. Thank you!",
                orderId, event.getTrackingNumber());
        send(orderId, NotificationType.DELIVERED, NotificationChannel.EMAIL, emailFor(event.getCustomerId()),
                "Your order " + orderId + " has been delivered", message);
        send(orderId, NotificationType.DELIVERED, NotificationChannel.SMS, phoneFor(event.getCustomerId()),
                null, message);
    }

    @Transactional(readOnly = true)
    public NotificationDTO get(Long id) {
        return toDTO(notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id.toString())));
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> getByOrderId(Long orderId) {
        return notificationRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(this::toDTO)
                .toList();
    }

    private void send(Long orderId, NotificationType type, NotificationChannel channel,
                      String recipient, String subject, String content) {
        if (notificationRepository.existsByOrderIdAndTypeAndChannel(orderId, type, channel)) {
            log.info("Notification already sent: order={} type={} channel={}, skipping", orderId, type, channel);
            return;
        }

        Notification notification = Notification.builder()
                .orderId(orderId)
                .type(type)
                .channel(channel)
                .recipient(recipient)
                .subject(subject)
                .content(content)
                .status(NotificationStatus.PENDING)
                .build();

        boolean delivered;
        try {
            notification = notificationRepository.save(notification);
            delivered = channel == NotificationChannel.EMAIL
                    ? emailService.send(recipient, subject, content)
                    : smsService.send(recipient, content);
        } catch (DataIntegrityViolationException duplicate) {
            // Concurrent redelivery raced the pre-check; the winner already notified the customer.
            log.info("Concurrent duplicate notification for order={} type={} channel={}, skipping",
                    orderId, type, channel);
            return;
        }

        notification.setStatus(delivered ? NotificationStatus.SENT : NotificationStatus.FAILED);
        if (delivered) {
            notification.setSentAt(LocalDateTime.now());
        }
        Notification saved = notificationRepository.save(notification);

        outboxPublisher.storeEvent(
                saved.getId().toString(),
                NotificationSentEvent.EVENT_TYPE,
                NotificationSentEvent.TOPIC,
                orderId.toString(),
                NotificationSentEvent.builder()
                        .notificationId(saved.getId().toString())
                        .orderId(orderId.toString())
                        .type(type.name())
                        .channel(channel.name())
                        .recipient(recipient)
                        .status(saved.getStatus().name())
                        .sentAt(saved.getSentAt())
                        .createdAt(saved.getCreatedAt())
                        .build());

        log.info("Notification {} recorded: order={} type={} channel={} status={}",
                saved.getId(), orderId, type, channel, saved.getStatus());
    }

    private String emailFor(String customerId) {
        return "customer-" + (customerId == null ? "unknown" : customerId) + "@example.com";
    }

    private String phoneFor(String customerId) {
        long id = 0;
        if (customerId != null) {
            try {
                id = Long.parseLong(customerId);
            } catch (NumberFormatException ignored) {
                // non-numeric customer ids fall back to the padded hash below
                id = Math.abs(customerId.hashCode());
            }
        }
        return String.format("+1555%07d", id % 10_000_000L);
    }

    private NotificationDTO toDTO(Notification n) {
        return NotificationDTO.builder()
                .id(n.getId())
                .orderId(n.getOrderId())
                .type(n.getType().name())
                .channel(n.getChannel().name())
                .recipient(n.getRecipient())
                .subject(n.getSubject())
                .content(n.getContent())
                .status(n.getStatus().name())
                .sentAt(n.getSentAt())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
