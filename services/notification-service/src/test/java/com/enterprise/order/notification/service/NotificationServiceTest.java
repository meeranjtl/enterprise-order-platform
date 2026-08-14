package com.enterprise.order.notification.service;

import com.enterprise.order.notification.entity.Notification;
import com.enterprise.order.notification.entity.NotificationChannel;
import com.enterprise.order.notification.entity.NotificationType;
import com.enterprise.order.notification.repository.NotificationRepository;
import com.enterprise.order.shared.events.NotificationSentEvent;
import com.enterprise.order.shared.events.OrderCreatedEvent;
import com.enterprise.order.shared.events.PaymentProcessedEvent;
import com.enterprise.order.shared.events.ShipmentCreatedEvent;
import com.enterprise.order.shared.outbox.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private SmsService smsService;

    @Mock
    private OutboxPublisher outboxPublisher;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, emailService, smsService, outboxPublisher);
    }

    @Test
    void orderCreatedSendsOrderConfirmedEmail() {
        when(notificationRepository.existsByOrderIdAndTypeAndChannel(
                100L, NotificationType.ORDER_CONFIRMED, NotificationChannel.EMAIL)).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(1L);
            return n;
        });
        when(emailService.send(anyString(), anyString(), anyString())).thenReturn(true);

        service.handleOrderCreated(orderCreatedEvent());

        verify(emailService).send(eq("customer-7@example.com"), anyString(), anyString());
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).storeEvent(
                eq("1"), eq(NotificationSentEvent.EVENT_TYPE),
                eq(NotificationSentEvent.TOPIC), eq("100"), payload.capture());
        NotificationSentEvent sent = (NotificationSentEvent) payload.getValue();
        assertEquals("ORDER_CONFIRMED", sent.getType());
        assertEquals("EMAIL", sent.getChannel());
        assertEquals("SENT", sent.getStatus());
    }

    @Test
    void duplicateNotificationIsSkipped() {
        when(notificationRepository.existsByOrderIdAndTypeAndChannel(
                100L, NotificationType.ORDER_CONFIRMED, NotificationChannel.EMAIL)).thenReturn(true);

        service.handleOrderCreated(orderCreatedEvent());

        verify(notificationRepository, never()).save(any());
        verifyNoInteractions(emailService, smsService, outboxPublisher);
    }

    @Test
    void shipmentCreatedSendsEmailAndSms() {
        when(notificationRepository.existsByOrderIdAndTypeAndChannel(
                100L, NotificationType.SHIPPED, NotificationChannel.EMAIL)).thenReturn(false);
        when(notificationRepository.existsByOrderIdAndTypeAndChannel(
                100L, NotificationType.SHIPPED, NotificationChannel.SMS)).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(1L);
            return n;
        });
        when(emailService.send(anyString(), anyString(), anyString())).thenReturn(true);
        when(smsService.send(anyString(), anyString())).thenReturn(true);

        service.handleShipmentCreated(shipmentCreatedEvent());

        verify(emailService).send(anyString(), anyString(), anyString());
        verify(smsService).send(anyString(), anyString());
        verify(outboxPublisher, times(2)).storeEvent(
                anyString(), eq(NotificationSentEvent.EVENT_TYPE),
                eq(NotificationSentEvent.TOPIC), anyString(), any());
    }

    @Test
    void paymentCompletedSendsPaymentReceivedEmail() {
        when(notificationRepository.existsByOrderIdAndTypeAndChannel(
                100L, NotificationType.PAYMENT_RECEIVED, NotificationChannel.EMAIL)).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(2L);
            return n;
        });
        when(emailService.send(anyString(), anyString(), anyString())).thenReturn(true);

        service.handlePaymentCompleted(paymentEvent());

        verify(emailService).send(eq("customer-7@example.com"), anyString(), anyString());
        verify(outboxPublisher).storeEvent(
                eq("2"), eq(NotificationSentEvent.EVENT_TYPE),
                eq(NotificationSentEvent.TOPIC), eq("100"), any());
    }

    private OrderCreatedEvent orderCreatedEvent() {
        return OrderCreatedEvent.builder()
                .orderId("100")
                .orderNumber("ORD-100")
                .customerId("7")
                .totalAmount(250.0)
                .build();
    }

    private ShipmentCreatedEvent shipmentCreatedEvent() {
        return ShipmentCreatedEvent.builder()
                .shipmentId("1")
                .orderId("100")
                .customerId("7")
                .trackingNumber("TRK-ABC123")
                .build();
    }

    private PaymentProcessedEvent paymentEvent() {
        return PaymentProcessedEvent.builder()
                .paymentId("PAY1")
                .orderId("100")
                .customerId("7")
                .amount(250.0)
                .transactionId("TX-1")
                .status(PaymentProcessedEvent.PaymentStatus.COMPLETED)
                .build();
    }
}
