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
 * One row per OrderCreatedEvent (unique order_id, INSERT ... DO NOTHING).
 * Anchors daily order counts so Kafka redeliveries never inflate totals.
 */
@Entity
@Table(
        name = "order_facts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_facts_order",
                columnNames = "order_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "ordered_date", nullable = false)
    private LocalDate orderedDate;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
