package com.enterprise.order.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyMetricDTO {
    private LocalDate metricDate;
    private Long totalOrders;
    private BigDecimal totalRevenue;
    private BigDecimal avgOrderValue;
    private Long completedOrders;
    private Long failedOrders;
    private Long distinctCustomers;
}
