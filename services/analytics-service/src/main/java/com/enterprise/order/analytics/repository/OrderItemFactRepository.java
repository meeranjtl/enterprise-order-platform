package com.enterprise.order.analytics.repository;

import com.enterprise.order.analytics.entity.OrderItemFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface OrderItemFactRepository extends JpaRepository<OrderItemFact, Long> {

    /**
     * Idempotent order-line anchor: DO NOTHING on redelivery.
     */
    @Modifying
    @Query(value = """
            INSERT INTO order_item_facts (order_id, product_id, quantity, unit_price, ordered_date, created_at)
            VALUES (:orderId, :productId, :quantity, :unitPrice, :orderedDate, CURRENT_TIMESTAMP)
            ON CONFLICT (order_id, product_id) DO NOTHING
            """, nativeQuery = true)
    int upsert(@Param("orderId") Long orderId,
               @Param("productId") Long productId,
               @Param("quantity") Integer quantity,
               @Param("unitPrice") BigDecimal unitPrice,
               @Param("orderedDate") LocalDate orderedDate);

    /** Products ordered on a given date — drives the reconciliation sweep. */
    @Query("SELECT DISTINCT i.productId FROM OrderItemFact i WHERE i.orderedDate = :orderedDate")
    List<Long> findDistinctProductIdsByOrderedDate(@Param("orderedDate") LocalDate orderedDate);
}
