package com.enterprise.order.payment.repository;

import com.enterprise.order.payment.entity.Payment;
import com.enterprise.order.payment.entity.PaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByStatusAndNextRetryAtLessThanEqual(
            PaymentStatus status,
            LocalDateTime retryAt);

    Optional<Payment> findByOrderId(Long orderId);
}
