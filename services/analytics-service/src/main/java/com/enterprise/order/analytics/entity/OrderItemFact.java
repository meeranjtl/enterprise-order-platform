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
 * One row per order line (unique order_id + product_id, INSERT ... DO NOTHING).
 * Source of truth for product_metrics recomputation.
 */
@Entity
@Table(
        name = "order_item_facts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_item_facts_order_product",
                columnNames = {"order_id", "product_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "ordered_date", nullable = false)
    private LocalDate orderedDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
