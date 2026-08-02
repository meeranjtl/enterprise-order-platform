package com.enterprise.order.payment.gateway;

public record PaymentResult(
        boolean successful,
        String transactionId,
        String failureReason) {

    public static PaymentResult success(String transactionId) {
        return new PaymentResult(true, transactionId, null);
    }

    public static PaymentResult failure(String failureReason) {
        return new PaymentResult(false, null, failureReason);
    }
}
