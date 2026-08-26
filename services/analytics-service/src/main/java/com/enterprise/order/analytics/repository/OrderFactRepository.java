package com.enterprise.order.analytics.repository;

import com.enterprise.order.analytics.entity.OrderFact;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderFactRepository extends JpaRepository<OrderFact, Long> {

    /**
     * Idempotent fact anchor: DO NOTHING on redelivery. Returns the number of
     * rows inserted (1 first time, 0 on replay).
     */
    @Modifying
    @Query(value = """
            INSERT INTO order_facts (order_id, customer_id, total_amount, ordered_date, ordered_at, created_at)
            VALUES (:orderId, :customerId, :totalAmount, :orderedDate, :orderedAt, CURRENT_TIMESTAMP)
            ON CONFLICT (order_id) DO NOTHING
            """, nativeQuery = true)
    int upsert(@Param("orderId") Long orderId,
               @Param("customerId") Long customerId,
               @Param("totalAmount") BigDecimal totalAmount,
               @Param("orderedDate") LocalDate orderedDate,
               @Param("orderedAt") LocalDateTime orderedAt);

    @Query("SELECT f.orderedDate FROM OrderFact f WHERE f.orderId = :orderId")
    Optional<LocalDate> findOrderedDateByOrderId(@Param("orderId") Long orderId);

    /** Dates with at least one order fact — drives the reconciliation sweep. */
    @Query("SELECT DISTINCT f.orderedDate FROM OrderFact f WHERE f.orderedDate >= :since")
    List<LocalDate> findDistinctOrderedDatesSince(@Param("since") LocalDate since);

    /** Single row: distinct ordering customers, total orders in the range. */
    @Query("""
            SELECT COUNT(DISTINCT f.customerId), COUNT(f.id)
            FROM OrderFact f
            WHERE f.orderedDate BETWEEN :from AND :to
            """)
    List<Object[]> countCustomersAndOrders(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Top customers by ordered amount in the range (customerId, amount, orders). */
    @Query("""
            SELECT f.customerId, SUM(f.totalAmount), COUNT(f.id)
            FROM OrderFact f
            WHERE f.orderedDate BETWEEN :from AND :to AND f.customerId IS NOT NULL
            GROUP BY f.customerId
            ORDER BY SUM(f.totalAmount) DESC
            """)
    List<Object[]> findTopCustomersByOrderedAmount(@Param("from") LocalDate from,
                                                   @Param("to") LocalDate to,
                                                   Pageable pageable);

    /** Lifetime distinct ordering customers — powers the summary KPI. */
    @Query("SELECT COUNT(DISTINCT f.customerId) FROM OrderFact f")
    long countDistinctCustomersLifetime();
}
