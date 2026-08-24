package com.enterprise.order.analytics.repository;

import com.enterprise.order.analytics.entity.OrderRevenue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRevenueRepository extends JpaRepository<OrderRevenue, Long> {

    /** Rows whose latest payment event landed inside the window. */
    List<OrderRevenue> findByPaidAtBetween(LocalDateTime from, LocalDateTime to);

    /**
     * Latest payment outcome wins: COMPLETED → REFUNDED transitions overwrite
     * the row; identical redeliveries rewrite the same values (no-op).
     */
    @Modifying
    @Query(value = """
            INSERT INTO order_revenue (order_id, customer_id, amount, payment_status,
                                         transaction_id, paid_at, created_at, updated_at)
            VALUES (:orderId, :customerId, :amount, :paymentStatus,
                    :transactionId, :paidAt, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (order_id) DO UPDATE SET
                customer_id = EXCLUDED.customer_id,
                amount = EXCLUDED.amount,
                payment_status = EXCLUDED.payment_status,
                transaction_id = EXCLUDED.transaction_id,
                paid_at = EXCLUDED.paid_at,
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int upsert(@Param("orderId") Long orderId,
               @Param("customerId") Long customerId,
               @Param("amount") BigDecimal amount,
               @Param("paymentStatus") String paymentStatus,
               @Param("transactionId") String transactionId,
               @Param("paidAt") LocalDateTime paidAt);
}
