package com.enterprise.order.shipping.entity;

/**
 * Shipment lifecycle:
 * PENDING   - created when payment completes; waiting for the packing list reply
 * SHIPPED   - packing list received, tracking number assigned, handed to carrier
 * DELIVERED - delivery confirmed (simulated via the deliver endpoint)
 */
public enum ShipmentStatus {
    PENDING, SHIPPED, DELIVERED
}
