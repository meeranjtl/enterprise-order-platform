package com.enterprise.order.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Customer analytics for a date range, attributed to the ORDER date
 * (order_facts). Events carry no customer master data, so metrics are
 * derived from ordering behaviour.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerMetricsDTO {
    private LocalDate from;
    private LocalDate to;
    private Long distinctCustomers;
    private Long totalOrders;
    private List<TopCustomerDTO> topCustomers;
}
