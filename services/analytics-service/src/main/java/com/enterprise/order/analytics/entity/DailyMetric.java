package com.enterprise.order.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Per-day business rollup. Never written incrementally — recomputed from the
 * fact tables (order_facts + order_revenue) on every event so redeliveries
 * can never corrupt the numbers.
 */
@Entity
@Table(
        name = "daily_metrics",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_metrics_date",
                columnNames = "metric_date"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "total_orders", nullable = false)
    private Long totalOrders;

    @Column(name = "total_revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalRevenue;

    @Column(name = "avg_order_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal avgOrderValue;

    @Column(name = "completed_orders", nullable = false)
    private Long completedOrders;

    @Column(name = "failed_orders", nullable = false)
    private Long failedOrders;

    @Column(name = "distinct_customers", nullable = false)
    private Long distinctCustomers;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
