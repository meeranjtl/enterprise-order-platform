package com.enterprise.order.shared.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentProcessedEvent {

    @JsonProperty("paymentId")
    private String paymentId;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("amount")
    private Double amount;

    @JsonProperty("status")
    private PaymentStatus status;

    @JsonProperty("transactionId")
    private String transactionId;

    @JsonProperty("failureReason")
    private String failureReason;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    public static final String TOPIC = "payment-events";
    public static final String EVENT_TYPE = "PaymentProcessed";

    public enum PaymentStatus {
        PENDING, COMPLETED, FAILED, REFUNDED
    }
}
