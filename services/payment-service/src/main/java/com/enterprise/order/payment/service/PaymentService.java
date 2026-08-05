package com.enterprise.order.payment.service;

import com.enterprise.order.payment.dto.CreatePaymentRequest;
import com.enterprise.order.payment.dto.PaymentDTO;
import com.enterprise.order.payment.entity.Payment;
import com.enterprise.order.payment.entity.PaymentStatus;
import com.enterprise.order.payment.gateway.PaymentGateway;
import com.enterprise.order.payment.gateway.PaymentResult;
import com.enterprise.order.payment.messaging.PaymentEventPublisher;
import com.enterprise.order.payment.repository.PaymentRepository;
import com.enterprise.order.shared.exception.BadRequestException;
import com.enterprise.order.shared.exception.ResourceNotFoundException;
import java.time.LocalDateTime;

import com.enterprise.order.shared.outbox.OutboxPublisher;
import com.enterprise.order.shared.events.PaymentProcessedEvent;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private static final int MAX_RETRIES = 3;

    private final PaymentRepository repository;
    private final PaymentGateway gateway;
    private final ObjectProvider<PaymentEventPublisher> publisher;
    private final OutboxPublisher outboxPublisher;

    public PaymentDTO create(CreatePaymentRequest request) {
        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .customerId(request.getCustomerId())
                .amount(request.getAmount())
                .method(request.getMethod())
                .status(PaymentStatus.PENDING)
                .retryCount(0)
                .build();

        return process(repository.save(payment), false);
    }

    @Transactional(readOnly = true)
    public PaymentDTO get(Long id) {
        return toDto(find(id));
    }

    public PaymentDTO retry(Long id) {
        Payment payment = find(id);

        if (payment.getStatus() != PaymentStatus.FAILED) {
            throw new BadRequestException("Only failed payments can be retried");
        }
        if (payment.getRetryCount() >= MAX_RETRIES) {
            throw new BadRequestException("Maximum retry attempts reached");
        }

        return process(payment, true);
    }

    public PaymentDTO refund(Long id) {
        Payment payment = find(id);

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new BadRequestException("Only completed payments can be refunded");
        }

        PaymentResult result = gateway.refund(payment);
        if (!result.successful()) {
            throw new BadRequestException("Refund failed: " + result.failureReason());
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setFailureReason(null);
        payment.setNextRetryAt(null);
        return saveAndPublish(payment);
    }

    @Scheduled(fixedDelayString = "${payment.retry.scan-delay-ms:1000}")
    public void retryDuePayments() {
        repository.findByStatusAndNextRetryAtLessThanEqual(
                        PaymentStatus.FAILED,
                        LocalDateTime.now())
                .stream()
                .filter(payment -> payment.getRetryCount() < MAX_RETRIES)
                .forEach(payment -> process(payment, true));
    }

    private PaymentDTO process(Payment payment, boolean retry) {
        payment.setStatus(PaymentStatus.PROCESSING);
        if (retry) {
            payment.setRetryCount(payment.getRetryCount() + 1);
        }

        PaymentResult result = gateway.process(payment);
        if (result.successful()) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setTransactionId(result.transactionId());
            payment.setFailureReason(null);
            payment.setNextRetryAt(null);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.failureReason());
            scheduleRetry(payment);
        }

        return saveAndPublish(payment);
    }

    private void scheduleRetry(Payment payment) {
        if (payment.getRetryCount() < MAX_RETRIES) {
            long delaySeconds = 1L << payment.getRetryCount();
            payment.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
        }
    }

    private PaymentDTO saveAndPublish(Payment payment) {
        PaymentDTO response = toDto(repository.save(payment));

        // Store event in outbox for reliable publishing
        PaymentProcessedEvent event = PaymentProcessedEvent.builder()
                .paymentId(payment.getId().toString())
                .orderId(payment.getOrderId().toString())
                .customerId(payment.getCustomerId().toString())
                .amount(payment.getAmount().doubleValue())
                .status(PaymentProcessedEvent.PaymentStatus.valueOf(payment.getStatus().name()))
                .transactionId(payment.getTransactionId())
                .failureReason(payment.getFailureReason())
                .createdAt(java.time.LocalDateTime.now())
                .build();

        outboxPublisher.storeEvent(payment.getId().toString(), PaymentProcessedEvent.EVENT_TYPE, PaymentProcessedEvent.TOPIC, payment.getId().toString(), event);

        // Also publish directly if Kafka publisher is available (backward compatibility)
        if (publisher != null) {
            Optional.ofNullable(publisher.getIfAvailable())
                    .ifPresent(eventPublisher -> eventPublisher.publish(response));
        }
        return response;
    }

    private Payment find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", String.valueOf(id)));
    }

    private PaymentDTO toDto(Payment payment) {
        return PaymentDTO.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .customerId(payment.getCustomerId())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .method(payment.getMethod())
                .transactionId(payment.getTransactionId())
                .failureReason(payment.getFailureReason())
                .retryCount(payment.getRetryCount())
                .nextRetryAt(payment.getNextRetryAt())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
