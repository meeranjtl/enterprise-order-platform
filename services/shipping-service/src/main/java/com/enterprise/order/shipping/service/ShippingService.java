package com.enterprise.order.shipping.service;

import com.enterprise.order.shared.dto.AddressDTO;
import com.enterprise.order.shared.events.PackingListProvidedEvent;
import com.enterprise.order.shared.events.PackingListRequestedEvent;
import com.enterprise.order.shared.events.PaymentProcessedEvent;
import com.enterprise.order.shared.events.ShipmentCreatedEvent;
import com.enterprise.order.shared.events.ShipmentDeliveredEvent;
import com.enterprise.order.shared.exception.BadRequestException;
import com.enterprise.order.shared.exception.ResourceNotFoundException;
import com.enterprise.order.shared.outbox.OutboxPublisher;
import com.enterprise.order.shipping.dto.CreateShipmentRequest;
import com.enterprise.order.shipping.dto.ShipmentDTO;
import com.enterprise.order.shipping.entity.Address;
import com.enterprise.order.shipping.entity.Shipment;
import com.enterprise.order.shipping.entity.ShipmentStatus;
import com.enterprise.order.shipping.repository.ShipmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Fulfillment workflow (Phase 9).
 *
 * <pre>
 * payment COMPLETED ──▶ createShipment (PENDING) ──▶ PackingListRequestedEvent
 *                                                        │ (inventory replies async)
 * PackingListProvidedEvent ──▶ markShipped (SHIPPED) ──▶ ShipmentCreatedEvent
 * POST /{id}/deliver ──▶ deliver (DELIVERED) ──────────▶ ShipmentDeliveredEvent
 * </pre>
 *
 * All event publishing goes through the transactional outbox so a crash between
 * the DB write and the Kafka send never loses an event.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingService {

    private static final String TRACKING_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int TRACKING_RANDOM_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShipmentRepository shipmentRepository;
    private final OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Manual shipment creation via REST. Idempotent per order: a second call for the
     * same orderId returns the existing shipment instead of creating a duplicate.
     */
    @Transactional
    public ShipmentDTO createShipment(CreateShipmentRequest request) {
        return shipmentRepository.findByOrderId(request.getOrderId())
                .map(this::toDTO)
                .orElseGet(() -> createPendingShipment(
                        request.getOrderId(),
                        request.getCustomerId(),
                        toEntity(request.getShippingAddress())));
    }

    /**
     * Event-driven entry point: a completed payment triggers shipment creation.
     * Events carry no address, so a simulated fulfillment address is used
     * (no inter-service HTTP during Phase 8+, per AGENTS.md).
     */
    @Transactional
    public ShipmentDTO createFromPayment(PaymentProcessedEvent event) {
        Long orderId = Long.valueOf(event.getOrderId());
        Long customerId = event.getCustomerId() == null ? null : Long.valueOf(event.getCustomerId());

        return shipmentRepository.findByOrderId(orderId)
                .map(existing -> {
                    log.info("Shipment already exists for order {} (id={}), skipping creation",
                            orderId, existing.getId());
                    return toDTO(existing);
                })
                .orElseGet(() -> {
                    try {
                        return createPendingShipment(orderId, customerId, simulatedAddress(orderId));
                    } catch (DataIntegrityViolationException duplicate) {
                        // Concurrent duplicate delivery of the same event — unique(order_id)
                        // constraint caught it; return the winner.
                        log.info("Concurrent shipment creation for order {}, returning existing", orderId);
                        return toDTO(shipmentRepository.findByOrderId(orderId).orElseThrow());
                    }
                });
    }

    /**
     * Async request/reply handler: inventory's packing list completes the shipment —
     * tracking number already assigned at creation, now the parcel is handed to the
     * carrier (SHIPPED) and the saga is notified via ShipmentCreatedEvent.
     * Idempotent: replies for an already-shipped/delivered shipment are ignored.
     */
    @Transactional
    public ShipmentDTO applyPackingList(PackingListProvidedEvent event) {
        Long orderId = Long.valueOf(event.getOrderId());
        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", "orderId=" + orderId));

        if (shipment.getStatus() != ShipmentStatus.PENDING) {
            log.info("Shipment for order {} already in status {}, ignoring packing list reply",
                    orderId, shipment.getStatus());
            return toDTO(shipment);
        }

        try {
            shipment.setPackingList(objectMapper.writeValueAsString(event.getItems()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize packing list for order " + orderId, e);
        }
        shipment.setStatus(ShipmentStatus.SHIPPED);
        shipment.setShippedAt(LocalDateTime.now());
        Shipment saved = shipmentRepository.save(shipment);

        outboxPublisher.storeEvent(
                saved.getId().toString(),
                ShipmentCreatedEvent.EVENT_TYPE,
                ShipmentCreatedEvent.TOPIC,
                saved.getOrderId().toString(),
                ShipmentCreatedEvent.builder()
                        .shipmentId(saved.getId().toString())
                        .orderId(saved.getOrderId().toString())
                        .customerId(saved.getCustomerId() == null ? null : saved.getCustomerId().toString())
                        .trackingNumber(saved.getTrackingNumber())
                        .shippedAt(saved.getShippedAt())
                        .createdAt(LocalDateTime.now())
                        .build());

        log.info("Shipment {} for order {} marked SHIPPED (tracking {})",
                saved.getId(), orderId, saved.getTrackingNumber());
        return toDTO(saved);
    }

    /** Delivery simulation: SHIPPED → DELIVERED and publishes ShipmentDeliveredEvent. */
    @Transactional
    public ShipmentDTO deliver(Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", shipmentId.toString()));

        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            return toDTO(shipment); // idempotent
        }
        if (shipment.getStatus() != ShipmentStatus.SHIPPED) {
            throw new BadRequestException(
                    "Shipment " + shipmentId + " cannot be delivered from status " + shipment.getStatus());
        }

        shipment.setStatus(ShipmentStatus.DELIVERED);
        shipment.setDeliveredAt(LocalDateTime.now());
        Shipment saved = shipmentRepository.save(shipment);

        outboxPublisher.storeEvent(
                saved.getId().toString(),
                ShipmentDeliveredEvent.EVENT_TYPE,
                ShipmentDeliveredEvent.TOPIC,
                saved.getOrderId().toString(),
                ShipmentDeliveredEvent.builder()
                        .shipmentId(saved.getId().toString())
                        .orderId(saved.getOrderId().toString())
                        .customerId(saved.getCustomerId() == null ? null : saved.getCustomerId().toString())
                        .trackingNumber(saved.getTrackingNumber())
                        .deliveredAt(saved.getDeliveredAt())
                        .createdAt(LocalDateTime.now())
                        .build());

        log.info("Shipment {} for order {} marked DELIVERED", saved.getId(), saved.getOrderId());
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public ShipmentDTO get(Long id) {
        return toDTO(shipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", id.toString())));
    }

    @Transactional(readOnly = true)
    public ShipmentDTO getByOrderId(Long orderId) {
        return toDTO(shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", "orderId=" + orderId)));
    }

    private ShipmentDTO createPendingShipment(Long orderId, Long customerId, Address address) {
        Shipment shipment = shipmentRepository.save(Shipment.builder()
                .orderId(orderId)
                .customerId(customerId)
                .trackingNumber(generateTrackingNumber())
                .shippingAddress(address)
                .status(ShipmentStatus.PENDING)
                .build());

        outboxPublisher.storeEvent(
                shipment.getId().toString(),
                PackingListRequestedEvent.EVENT_TYPE,
                PackingListRequestedEvent.TOPIC,
                orderId.toString(),
                PackingListRequestedEvent.builder()
                        .requestId(shipment.getId().toString())
                        .orderId(orderId.toString())
                        .shipmentId(shipment.getId().toString())
                        .customerId(customerId == null ? null : customerId.toString())
                        .createdAt(LocalDateTime.now())
                        .build());

        log.info("Created shipment {} for order {} (tracking {}), packing list requested",
                shipment.getId(), orderId, shipment.getTrackingNumber());
        return toDTO(shipment);
    }

    String generateTrackingNumber() {
        StringBuilder sb = new StringBuilder("TRK-");
        for (int i = 0; i < TRACKING_RANDOM_LENGTH; i++) {
            sb.append(TRACKING_ALPHABET.charAt(RANDOM.nextInt(TRACKING_ALPHABET.length())));
        }
        return sb.toString();
    }

    private Address simulatedAddress(Long orderId) {
        return Address.builder()
                .street("Fulfillment Center Drive")
                .buildingNumber("1")
                .city("Simulation City")
                .state("KA")
                .zipCode("560001")
                .country("India")
                .build();
    }

    private Address toEntity(AddressDTO dto) {
        if (dto == null) {
            return null;
        }
        return Address.builder()
                .street(dto.getStreet())
                .buildingNumber(dto.getBuildingNumber())
                .city(dto.getCity())
                .state(dto.getState())
                .zipCode(dto.getZipCode())
                .country(dto.getCountry())
                .build();
    }

    private AddressDTO toAddressDTO(Address address) {
        if (address == null) {
            return null;
        }
        return AddressDTO.builder()
                .street(address.getStreet())
                .buildingNumber(address.getBuildingNumber())
                .city(address.getCity())
                .state(address.getState())
                .zipCode(address.getZipCode())
                .country(address.getCountry())
                .build();
    }

    private ShipmentDTO toDTO(Shipment shipment) {
        return ShipmentDTO.builder()
                .id(shipment.getId())
                .orderId(shipment.getOrderId())
                .customerId(shipment.getCustomerId())
                .trackingNumber(shipment.getTrackingNumber())
                .status(shipment.getStatus().name())
                .shippingAddress(toAddressDTO(shipment.getShippingAddress()))
                .packingList(shipment.getPackingList())
                .shippedAt(shipment.getShippedAt())
                .deliveredAt(shipment.getDeliveredAt())
                .createdAt(shipment.getCreatedAt())
                .updatedAt(shipment.getUpdatedAt())
                .build();
    }
}
