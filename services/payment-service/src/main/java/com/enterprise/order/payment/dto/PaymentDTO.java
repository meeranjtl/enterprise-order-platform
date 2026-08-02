package com.enterprise.order.payment.dto;

import com.enterprise.order.payment.entity.PaymentMethod;
import com.enterprise.order.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PaymentDTO {
    Long id;
    Long orderId;
    Long customerId;
    BigDecimal amount;
    PaymentStatus status;
    PaymentMethod method;
    String transactionId;
    String failureReason;
    Integer retryCount;
    LocalDateTime nextRetryAt;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
