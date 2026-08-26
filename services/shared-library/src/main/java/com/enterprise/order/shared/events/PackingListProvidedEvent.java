package com.enterprise.order.shared.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Async request/reply pattern (Phase 9): inventory-service's reply to
 * {@link PackingListRequestedEvent}, listing the reserved items to pack.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PackingListProvidedEvent {

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("shipmentId")
    private String shipmentId;

    @JsonProperty("items")
    private List<PackingItem> items;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    public static final String TOPIC = "inventory-shipping-reply-events";
    public static final String EVENT_TYPE = "PackingListProvided";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PackingItem {

        @JsonProperty("productId")
        private String productId;

        @JsonProperty("quantity")
        private Integer quantity;
    }
}
