package com.enterprise.order.payment.gateway;

import com.enterprise.order.payment.entity.Payment;

public interface PaymentGateway {

    PaymentResult process(Payment payment);

    PaymentResult refund(Payment payment);
}
