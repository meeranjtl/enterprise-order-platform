package com.enterprise.order.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReservationRequest {
    @NotNull
    private Long productId;

    @NotNull
    private Long orderId;

    @NotNull
    @Min(1)
    private Integer quantity;
}
