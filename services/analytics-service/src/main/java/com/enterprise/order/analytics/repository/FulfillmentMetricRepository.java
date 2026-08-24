package com.enterprise.order.analytics.repository;

import com.enterprise.order.analytics.entity.FulfillmentMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FulfillmentMetricRepository extends JpaRepository<FulfillmentMetric, Long> {

    /**
     * Single row for orders placed in the window: avg ship seconds,
     * avg deliver seconds, shipped count, delivered count. The COALESCEs on the
     * SUMs matter: SUM over zero matching rows is NULL in SQL, not 0, and the
     * counts must stay non-null longs for the DTO (AVG is left nullable — no
     * shipped/delivered orders means no average to report).
     */
    @Query("""
            SELECT AVG(f.orderToShipSeconds),
                   AVG(f.orderToDeliverSeconds),
                   COALESCE(SUM(CASE WHEN f.shippedAt IS NOT NULL THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN f.deliveredAt IS NOT NULL THEN 1 ELSE 0 END), 0),
                   COUNT(f.id)
            FROM FulfillmentMetric f
            WHERE f.orderedAt BETWEEN :from AND :to
            """)
    List<Object[]> aggregateDurations(@Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    @Modifying
    @Query(value = """
            INSERT INTO fulfillment_metrics (order_id, ordered_at, created_at, updated_at)
            VALUES (:orderId, :orderedAt, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (order_id) DO UPDATE SET
                ordered_at = EXCLUDED.ordered_at,
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int upsertOrderedAt(@Param("orderId") Long orderId,
                        @Param("orderedAt") LocalDateTime orderedAt);

    @Modifying
    @Query(value = """
            INSERT INTO fulfillment_metrics (order_id, shipped_at, created_at, updated_at)
            VALUES (:orderId, :shippedAt, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (order_id) DO UPDATE SET
                shipped_at = EXCLUDED.shipped_at,
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int upsertShippedAt(@Param("orderId") Long orderId,
                        @Param("shippedAt") LocalDateTime shippedAt);

    @Modifying
    @Query(value = """
            INSERT INTO fulfillment_metrics (order_id, delivered_at, created_at, updated_at)
            VALUES (:orderId, :deliveredAt, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (order_id) DO UPDATE SET
                delivered_at = EXCLUDED.delivered_at,
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int upsertDeliveredAt(@Param("orderId") Long orderId,
                          @Param("deliveredAt") LocalDateTime deliveredAt);

    /**
     * Derives the duration columns whenever both endpoints are present.
     * Runs after every fulfillment upsert, so event arrival order is irrelevant.
     */
    @Modifying
    @Query(value = """
            UPDATE fulfillment_metrics SET
                order_to_ship_seconds = CASE
                    WHEN ordered_at IS NOT NULL AND shipped_at IS NOT NULL
                    THEN EXTRACT(EPOCH FROM (shipped_at - ordered_at))::BIGINT
                    END,
                order_to_deliver_seconds = CASE
                    WHEN ordered_at IS NOT NULL AND delivered_at IS NOT NULL
                    THEN EXTRACT(EPOCH FROM (delivered_at - ordered_at))::BIGINT
                    END,
                updated_at = CURRENT_TIMESTAMP
            WHERE order_id = :orderId
            """, nativeQuery = true)
    int recomputeDurations(@Param("orderId") Long orderId);
}
