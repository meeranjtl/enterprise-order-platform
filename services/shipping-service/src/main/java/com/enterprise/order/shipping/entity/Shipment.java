package com.enterprise.order.shipping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "shipments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "tracking_number", length = 40, unique = true)
    private String trackingNumber;

    @Embedded
    private Address shippingAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ShipmentStatus status;

    /** Serialized JSON array of packing items returned by inventory-service. */
    @Column(name = "packing_list", columnDefinition = "TEXT")
    private String packingList;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void setUpdatedAt() {
        updatedAt = LocalDateTime.now();
    }
}
