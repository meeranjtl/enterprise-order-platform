package com.enterprise.order.shipping.service;

import com.enterprise.order.shared.events.PackingListProvidedEvent;
import com.enterprise.order.shared.events.PackingListRequestedEvent;
import com.enterprise.order.shared.events.PaymentProcessedEvent;
import com.enterprise.order.shared.events.ShipmentCreatedEvent;
import com.enterprise.order.shared.events.ShipmentDeliveredEvent;
import com.enterprise.order.shared.exception.BadRequestException;
import com.enterprise.order.shared.outbox.OutboxPublisher;
import com.enterprise.order.shipping.dto.ShipmentDTO;
import com.enterprise.order.shipping.entity.Shipment;
import com.enterprise.order.shipping.entity.ShipmentStatus;
import com.enterprise.order.shipping.repository.ShipmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private OutboxPublisher outboxPublisher;

    private ShippingService service;

    @BeforeEach
    void setUp() {
        service = new ShippingService(shipmentRepository, outboxPublisher, new ObjectMapper());
    }

    @Test
    void createFromPaymentCreatesPendingShipmentAndRequestsPackingList() {
        when(shipmentRepository.findByOrderId(100L)).thenReturn(Optional.empty());
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(inv -> {
            Shipment s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });

        ShipmentDTO dto = service.createFromPayment(paymentEvent());

        assertEquals("PENDING", dto.getStatus());
        assertNotNull(dto.getTrackingNumber());
        assertTrue(dto.getTrackingNumber().startsWith("TRK-"));
        verify(outboxPublisher).storeEvent(
                eq("1"), eq(PackingListRequestedEvent.EVENT_TYPE),
                eq(PackingListRequestedEvent.TOPIC), eq("100"), any());
    }

    @Test
    void createFromPaymentIsIdempotentPerOrder() {
        Shipment existing = Shipment.builder().id(9L).orderId(100L).status(ShipmentStatus.PENDING).build();
        when(shipmentRepository.findByOrderId(100L)).thenReturn(Optional.of(existing));

        ShipmentDTO dto = service.createFromPayment(paymentEvent());

        assertEquals(9L, dto.getId());
        verify(shipmentRepository, never()).save(any());
        verifyNoInteractions(outboxPublisher);
    }

    @Test
    void applyPackingListMarksShipmentShippedAndPublishesEvent() {
        Shipment pending = Shipment.builder()
                .id(1L).orderId(100L).customerId(7L)
                .trackingNumber("TRK-ABC").status(ShipmentStatus.PENDING).build();
        when(shipmentRepository.findByOrderId(100L)).thenReturn(Optional.of(pending));
        when(shipmentRepository.save(any(Shipment.class))).thenReturn(pending);

        ShipmentDTO dto = service.applyPackingList(packingReply());

        assertEquals("SHIPPED", dto.getStatus());
        assertNotNull(pending.getShippedAt());
        assertNotNull(pending.getPackingList());
        verify(outboxPublisher).storeEvent(
                eq("1"), eq(ShipmentCreatedEvent.EVENT_TYPE),
                eq(ShipmentCreatedEvent.TOPIC), eq("100"), any());
    }

    @Test
    void applyPackingListIgnoresAlreadyShippedShipment() {
        Shipment shipped = Shipment.builder().id(1L).orderId(100L).status(ShipmentStatus.SHIPPED).build();
        when(shipmentRepository.findByOrderId(100L)).thenReturn(Optional.of(shipped));

        service.applyPackingList(packingReply());

        verify(shipmentRepository, never()).save(any());
        verifyNoInteractions(outboxPublisher);
    }

    @Test
    void deliverTransitionsShippedToDeliveredAndPublishesEvent() {
        Shipment shipped = Shipment.builder()
                .id(1L).orderId(100L).customerId(7L)
                .trackingNumber("TRK-ABC").status(ShipmentStatus.SHIPPED).build();
        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipped));
        when(shipmentRepository.save(any(Shipment.class))).thenReturn(shipped);

        ShipmentDTO dto = service.deliver(1L);

        assertEquals("DELIVERED", dto.getStatus());
        assertNotNull(shipped.getDeliveredAt());
        verify(outboxPublisher).storeEvent(
                eq("1"), eq(ShipmentDeliveredEvent.EVENT_TYPE),
                eq(ShipmentDeliveredEvent.TOPIC), eq("100"), any());
    }

    @Test
    void deliverRejectsShipmentThatIsNotShipped() {
        Shipment pending = Shipment.builder().id(1L).orderId(100L).status(ShipmentStatus.PENDING).build();
        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(pending));

        assertThrows(BadRequestException.class, () -> service.deliver(1L));
        verifyNoInteractions(outboxPublisher);
    }

    @Test
    void trackingNumberHasExpectedFormat() {
        String trackingNumber = service.generateTrackingNumber();

        assertTrue(trackingNumber.startsWith("TRK-"));
        assertEquals(16, trackingNumber.length());
    }

    private PaymentProcessedEvent paymentEvent() {
        return PaymentProcessedEvent.builder()
                .orderId("100")
                .customerId("7")
                .amount(250.0)
                .status(PaymentProcessedEvent.PaymentStatus.COMPLETED)
                .build();
    }

    private PackingListProvidedEvent packingReply() {
        return PackingListProvidedEvent.builder()
                .requestId("1")
                .orderId("100")
                .shipmentId("1")
                .items(List.of(
                        PackingListProvidedEvent.PackingItem.builder().productId("50").quantity(2).build()))
                .build();
    }
}
