package com.enterprise.order.shared.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReservedEvent {

    @JsonProperty("reservationId")
    private String reservationId;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("productId")
    private String productId;

    @JsonProperty("quantity")
    private Integer quantity;

    @JsonProperty("status")
    private ReservationStatus status;

    @JsonProperty("failureReason")
    private String failureReason;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    public static final String TOPIC = "inventory-events";
    public static final String EVENT_TYPE = "InventoryReserved";

    public enum ReservationStatus {
        PENDING, CONFIRMED, FAILED, RELEASED
    }
}
