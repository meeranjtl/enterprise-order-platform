package com.enterprise.order.shared.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Async request/reply pattern (Phase 9): shipping-service asks inventory-service
 * for the packing list of a paid order. Inventory replies with
 * {@link PackingListProvidedEvent} on {@code inventory-shipping-reply-events}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PackingListRequestedEvent {

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("shipmentId")
    private String shipmentId;

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    public static final String TOPIC = "inventory-shipping-request-events";
    public static final String EVENT_TYPE = "PackingListRequested";
}
