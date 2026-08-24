package com.enterprise.order.analytics.repository;

import com.enterprise.order.analytics.entity.DailyMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyMetricRepository extends JpaRepository<DailyMetric, Long> {

    Optional<DailyMetric> findByMetricDate(LocalDate metricDate);

    List<DailyMetric> findByMetricDateBetweenOrderByMetricDateAsc(LocalDate from, LocalDate to);

    /**
     * Lifetime totals across all daily rollups: orders, revenue, completed, failed.
     * Spring Data always returns aggregate SELECTs as {@code List<Object[]>} (one row);
     * declaring the return type as a bare {@code Object[]} produces a nested array.
     */
    @Query("""
            SELECT COALESCE(SUM(d.totalOrders), 0),
                   COALESCE(SUM(d.totalRevenue), 0),
                   COALESCE(SUM(d.completedOrders), 0),
                   COALESCE(SUM(d.failedOrders), 0)
            FROM DailyMetric d
            """)
    List<Object[]> sumLifetimeTotals();

    /**
     * Recomputes the whole day's rollup from the fact tables. Idempotent by
     * construction: replaying any event (any number of times, in any order)
     * converges to the same row. Revenue/completed/failed attribute to the
     * ORDER date (cohort view); payment-date views are served from
     * order_revenue.paid_at by the report APIs.
     */
    @Modifying
    @Query(value = """
            INSERT INTO daily_metrics (metric_date, total_orders, total_revenue, avg_order_value,
                                         completed_orders, failed_orders, distinct_customers,
                                         created_at, updated_at)
            SELECT :metricDate,
                   s.total_orders,
                   s.total_revenue,
                   CASE WHEN s.completed_orders > 0
                        THEN ROUND(s.total_revenue / NULLIF(s.completed_orders, 0), 2)
                        ELSE 0 END,
                   s.completed_orders,
                   s.failed_orders,
                   s.distinct_customers,
                   CURRENT_TIMESTAMP,
                   CURRENT_TIMESTAMP
            FROM (
                SELECT COUNT(f.order_id) AS total_orders,
                       COALESCE(SUM(CASE WHEN r.payment_status = 'COMPLETED' THEN r.amount ELSE 0 END), 0) AS total_revenue,
                       COALESCE(SUM(CASE WHEN r.payment_status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completed_orders,
                       COALESCE(SUM(CASE WHEN r.payment_status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failed_orders,
                       COUNT(DISTINCT f.customer_id) AS distinct_customers
                FROM order_facts f
                LEFT JOIN order_revenue r ON r.order_id = f.order_id
                WHERE f.ordered_date = :metricDate
            ) s
            ON CONFLICT (metric_date) DO UPDATE SET
                total_orders = EXCLUDED.total_orders,
                total_revenue = EXCLUDED.total_revenue,
                avg_order_value = EXCLUDED.avg_order_value,
                completed_orders = EXCLUDED.completed_orders,
                failed_orders = EXCLUDED.failed_orders,
                distinct_customers = EXCLUDED.distinct_customers,
                updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int recomputeForDate(@Param("metricDate") LocalDate metricDate);
}
