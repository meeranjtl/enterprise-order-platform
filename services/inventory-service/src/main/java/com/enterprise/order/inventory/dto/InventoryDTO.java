package com.enterprise.order.inventory.dto;

import com.enterprise.order.inventory.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDTO {
    private Long productId;
    private Integer totalQuantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private Long transactionId;
    private TransactionType transactionType;
    private LocalDateTime lastUpdated;
}
