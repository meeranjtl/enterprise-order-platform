package com.enterprise.order.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.enterprise.order.payment.dto.CreatePaymentRequest;
import com.enterprise.order.payment.entity.Payment;
import com.enterprise.order.payment.entity.PaymentMethod;
import com.enterprise.order.payment.entity.PaymentStatus;
import com.enterprise.order.payment.gateway.PaymentGateway;
import com.enterprise.order.payment.gateway.PaymentResult;
import com.enterprise.order.payment.repository.PaymentRepository;
import com.enterprise.order.shared.outbox.OutboxPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository repository;

    @Mock
    private PaymentGateway gateway;

    @Mock
    private OutboxPublisher outboxPublisher;

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private PaymentService service;

    @Test
    void completesSuccessfulPayment() {
        Payment payment = payment();
        when(repository.save(any())).thenReturn(payment);
        when(gateway.process(any())).thenReturn(PaymentResult.success("tx-1"));

        assertEquals(PaymentStatus.COMPLETED, service.create(request()).getStatus());
    }

    @Test
    void schedulesRetryOnFailure() {
        Payment payment = payment();
        when(repository.save(any())).thenReturn(payment);
        when(gateway.process(any())).thenReturn(PaymentResult.failure("declined"));

        assertEquals(PaymentStatus.FAILED, service.create(request()).getStatus());
        assertNotNull(payment.getNextRetryAt());
    }

    private Payment payment() {
        // Phase 8: saveAndPublish builds a PaymentProcessedEvent from the saved entity,
        // so orderId/customerId/amount must be populated.
        return Payment.builder()
                .id(1L)
                .orderId(4L)
                .customerId(5L)
                .amount(new BigDecimal("10.00"))
                .retryCount(0)
                .status(PaymentStatus.PENDING)
                .build();
    }

    private CreatePaymentRequest request() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderId(4L);
        request.setCustomerId(5L);
        request.setAmount(new BigDecimal("10.00"));
        request.setMethod(PaymentMethod.CREDIT_CARD);
        return request;
    }
}
