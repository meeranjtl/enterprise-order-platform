package com.enterprise.order.payment.gateway;

import com.enterprise.order.payment.entity.Payment;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult process(Payment payment) {
        boolean approved = ThreadLocalRandom.current().nextDouble() < 0.8;

        if (approved) {
            return PaymentResult.success("mock_" + UUID.randomUUID());
        }

        return PaymentResult.failure("Insufficient funds");
    }

    @Override
    public PaymentResult refund(Payment payment) {
        return PaymentResult.success("refund_" + UUID.randomUUID());
    }
}
