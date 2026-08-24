package com.enterprise.order.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Product performance aggregated over a date range. Product names are
 * intentionally absent — events carry productId only (Phase 10 decision);
 * the Phase 13 UI enriches names via product-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPerformanceDTO {
    private Long productId;
    private Long unitsSold;
    private BigDecimal revenue;
    private Long timesInOrder;
}
