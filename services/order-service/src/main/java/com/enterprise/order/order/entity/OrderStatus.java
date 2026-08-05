package com.enterprise.order.order.entity;

public enum OrderStatus {
    PENDING,
    VALIDATED,
    PAYMENT_PENDING,
    PAYMENT_APPROVED,   // Phase 8 saga: payment completed successfully
    PAYMENT_REJECTED,   // Phase 8 saga: payment failed; inventory compensation runs
    CANCELLED,
    FAILED,
    SHIPPED,
    COMPLETED
}
