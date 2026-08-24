package com.enterprise.order.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Revenue report over a date range. Rows are windowed by the LATEST payment
 * event time (order_revenue.paid_at): a refund lands on the day it happened,
 * and the same order never counts in both gross and refunded.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueReportDTO {
    private LocalDate from;
    private LocalDate to;
    private BigDecimal grossRevenue;
    private BigDecimal refundedAmount;
    private BigDecimal netRevenue;
    private Long completedPayments;
    private Long refundedPayments;
}
