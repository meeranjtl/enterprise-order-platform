package com.enterprise.order.analytics.service;

import com.enterprise.order.analytics.dto.AnalyticsSummaryDTO;
import com.enterprise.order.analytics.dto.CustomerMetricsDTO;
import com.enterprise.order.analytics.dto.DailyMetricDTO;
import com.enterprise.order.analytics.dto.FulfillmentMetricsDTO;
import com.enterprise.order.analytics.dto.ProductPerformanceDTO;
import com.enterprise.order.analytics.dto.RevenueReportDTO;
import com.enterprise.order.analytics.dto.TopCustomerDTO;
import com.enterprise.order.analytics.entity.OrderRevenue;
import com.enterprise.order.analytics.mapper.AnalyticsMapper;
import com.enterprise.order.analytics.repository.DailyMetricRepository;
import com.enterprise.order.analytics.repository.FulfillmentMetricRepository;
import com.enterprise.order.analytics.repository.OrderFactRepository;
import com.enterprise.order.analytics.repository.OrderRevenueRepository;
import com.enterprise.order.analytics.repository.ProductMetricRepository;
import com.enterprise.order.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Read-side report APIs (Phase 10, Sprint 3).
 *
 * Attribution rules:
 * <ul>
 *   <li>daily-metrics / customer-metrics attribute to the ORDER date
 *       (order_facts.ordered_date).</li>
 *   <li>revenue windows rows by the LATEST payment event time
 *       (order_revenue.paid_at), so refunds land on the day they happen.</li>
 *   <li>fulfillment-metrics windows by ordered_at.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsReportService {

    static final int DEFAULT_RANGE_DAYS = 30;
    static final int MAX_RANGE_DAYS = 366;
    static final int DEFAULT_TOP_N = 5;

    private static final String SORT_BY_REVENUE = "revenue";
    private static final String SORT_BY_UNITS = "units";

    private final DailyMetricRepository dailyMetricRepository;
    private final ProductMetricRepository productMetricRepository;
    private final OrderRevenueRepository orderRevenueRepository;
    private final OrderFactRepository orderFactRepository;
    private final FulfillmentMetricRepository fulfillmentMetricRepository;
    private final AnalyticsMapper analyticsMapper;

    public List<DailyMetricDTO> getDailyMetrics(LocalDate from, LocalDate to) {
        DateRange range = resolveRange(from, to);
        return dailyMetricRepository
                .findByMetricDateBetweenOrderByMetricDateAsc(range.from(), range.to())
                .stream()
                .map(analyticsMapper::toDTO)
                .toList();
    }

    public List<ProductPerformanceDTO> getProductMetrics(LocalDate from, LocalDate to,
                                                         String sortBy, Integer limit) {
        DateRange range = resolveRange(from, to);
        Comparator<ProductPerformanceDTO> comparator = comparatorFor(sortBy);
        int topN = normalizeLimit(limit);

        return productMetricRepository.aggregateByProduct(range.from(), range.to()).stream()
                .map(this::toProductPerformance)
                .sorted(comparator.reversed())
                .limit(topN)
                .toList();
    }

    public RevenueReportDTO getRevenueReport(LocalDate from, LocalDate to) {
        DateRange range = resolveRange(from, to);
        List<OrderRevenue> rows = orderRevenueRepository.findByPaidAtBetween(
                range.from().atStartOfDay(),
                range.to().plusDays(1).atStartOfDay());

        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        long completedCount = 0;
        long refundedCount = 0;

        for (OrderRevenue row : rows) {
            if ("COMPLETED".equals(row.getPaymentStatus())) {
                gross = gross.add(row.getAmount());
                completedCount++;
            } else if ("REFUNDED".equals(row.getPaymentStatus())) {
                refunded = refunded.add(row.getAmount());
                refundedCount++;
            }
            // FAILED rows carry the attempted amount but never count as revenue.
        }

        return RevenueReportDTO.builder()
                .from(range.from())
                .to(range.to())
                .grossRevenue(gross)
                .refundedAmount(refunded)
                .netRevenue(gross.subtract(refunded))
                .completedPayments(completedCount)
                .refundedPayments(refundedCount)
                .build();
    }

    public CustomerMetricsDTO getCustomerMetrics(LocalDate from, LocalDate to, Integer limit) {
        DateRange range = resolveRange(from, to);
        int topN = normalizeLimit(limit);

        Object[] counts = first(orderFactRepository.countCustomersAndOrders(range.from(), range.to()));
        long distinctCustomers = ((Number) counts[0]).longValue();
        long totalOrders = ((Number) counts[1]).longValue();

        List<TopCustomerDTO> topCustomers = orderFactRepository
                .findTopCustomersByOrderedAmount(range.from(), range.to(), PageRequest.of(0, topN))
                .stream()
                .map(row -> TopCustomerDTO.builder()
                        .customerId(((Number) row[0]).longValue())
                        .totalOrderedAmount(toBigDecimal(row[1]))
                        .orderCount(((Number) row[2]).longValue())
                        .build())
                .toList();

        return CustomerMetricsDTO.builder()
                .from(range.from())
                .to(range.to())
                .distinctCustomers(distinctCustomers)
                .totalOrders(totalOrders)
                .topCustomers(topCustomers)
                .build();
    }

    public FulfillmentMetricsDTO getFulfillmentMetrics(LocalDate from, LocalDate to) {
        DateRange range = resolveRange(from, to);
        Object[] row = first(fulfillmentMetricRepository.aggregateDurations(
                range.from().atStartOfDay(),
                range.to().plusDays(1).atStartOfDay()));

        return FulfillmentMetricsDTO.builder()
                .from(range.from())
                .to(range.to())
                .avgOrderToShipSeconds(toDouble(row[0]))
                .avgOrderToDeliverSeconds(toDouble(row[1]))
                .shippedOrders(((Number) row[2]).longValue())
                .deliveredOrders(((Number) row[3]).longValue())
                .trackedOrders(((Number) row[4]).longValue())
                .build();
    }

    public AnalyticsSummaryDTO getSummary() {
        Object[] totals = first(dailyMetricRepository.sumLifetimeTotals());
        long totalOrders = ((Number) totals[0]).longValue();
        BigDecimal totalRevenue = toBigDecimal(totals[1]);
        long completedOrders = ((Number) totals[2]).longValue();
        long failedOrders = ((Number) totals[3]).longValue();

        BigDecimal avgOrderValue = completedOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(completedOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<ProductPerformanceDTO> topProducts = productMetricRepository
                .aggregateByProductLifetime().stream()
                .map(this::toProductPerformance)
                .sorted(Comparator.comparing(ProductPerformanceDTO::getRevenue).reversed())
                .limit(DEFAULT_TOP_N)
                .toList();

        return AnalyticsSummaryDTO.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .completedOrders(completedOrders)
                .failedOrders(failedOrders)
                .avgOrderValue(avgOrderValue)
                .distinctCustomers(orderFactRepository.countDistinctCustomersLifetime())
                .topProducts(topProducts)
                .build();
    }

    // --- helpers -------------------------------------------------------------

    private DateRange resolveRange(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS - 1L);

        if (start.isAfter(end)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new BadRequestException("Date range must not exceed " + MAX_RANGE_DAYS + " days");
        }
        return new DateRange(start, end);
    }

    private Comparator<ProductPerformanceDTO> comparatorFor(String sortBy) {
        if (sortBy == null || SORT_BY_REVENUE.equalsIgnoreCase(sortBy)) {
            return Comparator.comparing(p -> p.getRevenue() == null ? BigDecimal.ZERO : p.getRevenue());
        }
        if (SORT_BY_UNITS.equalsIgnoreCase(sortBy)) {
            return Comparator.comparing(p -> p.getUnitsSold() == null ? 0L : p.getUnitsSold());
        }
        throw new BadRequestException("sortBy must be either '" + SORT_BY_REVENUE + "' or '" + SORT_BY_UNITS + "'");
    }

    private int normalizeLimit(Integer limit) {
        return limit == null || limit <= 0 ? DEFAULT_TOP_N : limit;
    }

    private ProductPerformanceDTO toProductPerformance(Object[] row) {
        return ProductPerformanceDTO.builder()
                .productId(((Number) row[0]).longValue())
                .unitsSold(((Number) row[1]).longValue())
                .revenue(toBigDecimal(row[2]))
                .timesInOrder(((Number) row[3]).longValue())
                .build();
    }

    private Object[] first(List<Object[]> rows) {
        if (rows.isEmpty()) {
            // Aggregate queries always return exactly one row; guard for safety.
            return new Object[]{0L, 0L, 0L, 0L, 0L};
        }
        return rows.get(0);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        return BigDecimal.valueOf(((Number) value).doubleValue());
    }

    private Double toDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
