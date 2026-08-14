package com.enterprise.order.shared.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Published by shipping-service when a shipment is packed and handed to the
 * carrier (status SHIPPED, tracking number assigned). The order saga uses it
 * to transition the order PAYMENT_APPROVED → SHIPPED.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentCreatedEvent {

    @JsonProperty("shipmentId")
    private String shipmentId;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("trackingNumber")
    private String trackingNumber;

    @JsonProperty("shippedAt")
    private LocalDateTime shippedAt;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    public static final String TOPIC = "shipping-events";
    public static final String EVENT_TYPE = "ShipmentCreated";
}
