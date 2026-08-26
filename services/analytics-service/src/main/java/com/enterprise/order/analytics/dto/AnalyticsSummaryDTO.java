package com.enterprise.order.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lifetime KPI snapshot: everything the platform has aggregated so far.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSummaryDTO {
    private Long totalOrders;
    private BigDecimal totalRevenue;
    private Long completedOrders;
    private Long failedOrders;
    private BigDecimal avgOrderValue;
    private Long distinctCustomers;
    private List<ProductPerformanceDTO> topProducts;
}
