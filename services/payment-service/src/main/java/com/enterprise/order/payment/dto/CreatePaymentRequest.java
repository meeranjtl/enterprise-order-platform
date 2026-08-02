package com.enterprise.order.payment.dto;

import com.enterprise.order.payment.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class CreatePaymentRequest {

    @NotNull
    @Positive
    private Long orderId;

    @NotNull
    @Positive
    private Long customerId;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 17, fraction = 2)
    private BigDecimal amount;

    @NotNull
    private PaymentMethod method;
}
