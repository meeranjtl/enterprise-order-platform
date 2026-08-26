package com.enterprise.order.analytics.repository;

import com.enterprise.order.analytics.entity.ProductMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductMetricRepository extends JpaRepository<ProductMetric, Long> {

    Optional<ProductMetric> findByMetricDateAndProductId(LocalDate metricDate, Long productId);

    List<ProductMetric> findByMetricDateBetweenOrderByRevenueDesc(LocalDate from, LocalDate to);

    /**
     * Per-product aggregation over a date range: productId, units, revenue,
     * times-in-order. Sorted/limited in the service layer.
     */
    @Query("""
            SELECT p.productId,
                   COALESCE(SUM(p.unitsSold), 0),
                   COALESCE(SUM(p.revenue), 0),
                   COALESCE(SUM(p.timesInOrder), 0)
            FROM ProductMetric p
            WHERE p.metricDate BETWEEN :from AND :to
            GROUP BY p.productId
            """)
    List<Object[]> aggregateByProduct(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Lifetime per-product aggregation — powers the summary top-products KPI. */
    @Query("""
            SELECT p.productId,
                   COALESCE(SUM(p.unitsSold), 0),
                   COALESCE(SUM(p.revenue), 0),
                   COALESCE(SUM(p.timesInOrder), 0)
            FROM ProductMetric p
            GROUP BY p.productId
            """)
    List<Object[]> aggregateByProductLifetime();

    /**
     * Recomputes one product's day rollup from order_item_facts. Idempotent —
     * see DailyMetricRepository.recomputeForDate for the rationale.
     */
    @Modifying
    @Query(value = """
            INSERT INTO product_metrics (metric_date, product_id, units_sold, revenue, times_in_order,
                                           created_at, updated_at)
            SELECT :metricDate,
                   :productId,
                   COALESCE(SUM(quantity), 0),
                   COALESCE(SUM(quantity * unit_price), 0),
                   COUNT(*),
                   CURRENT_TIMESTAMP,
                   CURRENT_TIMESTAMP
            FROM order_item_facts
            WHERE ordered_date = :metricDate AND product_id = :productId
            ON CONFLICT (metric_date, product_id) DO UPDATE SET
                units_sold = EXCLUDED.units_sold,
                revenue = EXCLUDED.revenue,
                times_in_order = EXCLUDED.times_in_order,
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int recompute(@Param("metricDate") LocalDate metricDate,
                  @Param("productId") Long productId);
}
