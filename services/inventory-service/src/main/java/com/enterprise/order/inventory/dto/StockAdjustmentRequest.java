package com.enterprise.order.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StockAdjustmentRequest {
    @NotNull
    private Long productId;

    @NotNull
    private Integer quantity;

    @NotBlank
    private String reason;
}
