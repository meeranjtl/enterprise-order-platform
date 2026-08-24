package com.enterprise.order.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Fulfillment timing averages for orders placed in the date range
 * (windowed by ordered_at).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FulfillmentMetricsDTO {
    private LocalDate from;
    private LocalDate to;
    private Long trackedOrders;
    private Long shippedOrders;
    private Long deliveredOrders;
    private Double avgOrderToShipSeconds;
    private Double avgOrderToDeliverSeconds;
}
