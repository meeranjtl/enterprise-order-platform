package com.enterprise.order.analytics.service;

import com.enterprise.order.analytics.dto.AnalyticsSummaryDTO;
import com.enterprise.order.analytics.dto.CustomerMetricsDTO;
import com.enterprise.order.analytics.dto.DailyMetricDTO;
import com.enterprise.order.analytics.dto.FulfillmentMetricsDTO;
import com.enterprise.order.analytics.dto.ProductPerformanceDTO;
import com.enterprise.order.analytics.dto.RevenueReportDTO;
import com.enterprise.order.analytics.entity.DailyMetric;
import com.enterprise.order.analytics.entity.OrderRevenue;
import com.enterprise.order.analytics.mapper.AnalyticsMapper;
import com.enterprise.order.analytics.repository.DailyMetricRepository;
import com.enterprise.order.analytics.repository.FulfillmentMetricRepository;
import com.enterprise.order.analytics.repository.OrderFactRepository;
import com.enterprise.order.analytics.repository.OrderRevenueRepository;
import com.enterprise.order.analytics.repository.ProductMetricRepository;
import com.enterprise.order.shared.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsReportServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 15);

    @Mock
    private DailyMetricRepository dailyMetricRepository;
    @Mock
    private ProductMetricRepository productMetricRepository;
    @Mock
    private OrderRevenueRepository orderRevenueRepository;
    @Mock
    private OrderFactRepository orderFactRepository;
    @Mock
    private FulfillmentMetricRepository fulfillmentMetricRepository;

    private AnalyticsReportService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsReportService(dailyMetricRepository, productMetricRepository,
                orderRevenueRepository, orderFactRepository, fulfillmentMetricRepository,
                Mappers.getMapper(AnalyticsMapper.class));
    }

    @Test
    void getDailyMetrics_mapsEntitiesInRange() {
        DailyMetric metric = DailyMetric.builder()
                .metricDate(FROM)
                .totalOrders(3L)
                .totalRevenue(new BigDecimal("300.00"))
                .avgOrderValue(new BigDecimal("150.00"))
                .completedOrders(2L)
                .failedOrders(1L)
                .distinctCustomers(2L)
                .build();
        when(dailyMetricRepository.findByMetricDateBetweenOrderByMetricDateAsc(FROM, TO))
                .thenReturn(List.of(metric));

        List<DailyMetricDTO> result = service.getDailyMetrics(FROM, TO);

        assertEquals(1, result.size());
        assertEquals(FROM, result.get(0).getMetricDate());
        assertEquals(3L, result.get(0).getTotalOrders());
        assertEquals(0, new BigDecimal("300.00").compareTo(result.get(0).getTotalRevenue()));
    }

    @Test
    void getDailyMetrics_defaultsToLast30DaysEndingToday() {
        when(dailyMetricRepository.findByMetricDateBetweenOrderByMetricDateAsc(any(), any()))
                .thenReturn(List.of());

        service.getDailyMetrics(null, TO);

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(dailyMetricRepository).findByMetricDateBetweenOrderByMetricDateAsc(fromCaptor.capture(), any());
        assertEquals(TO.minusDays(AnalyticsReportService.DEFAULT_RANGE_DAYS - 1L), fromCaptor.getValue());
    }

    @Test
    void getDailyMetrics_rejectsFromAfterTo() {
        assertThrows(BadRequestException.class, () -> service.getDailyMetrics(TO, FROM));
    }

    @Test
    void getDailyMetrics_rejectsRangeOverOneYear() {
        assertThrows(BadRequestException.class,
                () -> service.getDailyMetrics(FROM.minusYears(2), TO));
    }

    @Test
    void getProductMetrics_sortsByRevenueByDefaultAndAppliesLimit() {
        when(productMetricRepository.aggregateByProduct(FROM, TO)).thenReturn(List.of(
                new Object[]{11L, 5L, new BigDecimal("500.00"), 3L},
                new Object[]{12L, 9L, new BigDecimal("300.00"), 2L},
                new Object[]{13L, 1L, new BigDecimal("900.00"), 1L}));

        List<ProductPerformanceDTO> result = service.getProductMetrics(FROM, TO, null, 2);

        assertEquals(2, result.size());
        assertEquals(13L, result.get(0).getProductId());
        assertEquals(11L, result.get(1).getProductId());
    }

    @Test
    void getProductMetrics_sortsByUnitsWhenRequested() {
        when(productMetricRepository.aggregateByProduct(FROM, TO)).thenReturn(List.of(
                new Object[]{11L, 5L, new BigDecimal("500.00"), 3L},
                new Object[]{12L, 9L, new BigDecimal("300.00"), 2L}));

        List<ProductPerformanceDTO> result = service.getProductMetrics(FROM, TO, "units", null);

        assertEquals(12L, result.get(0).getProductId());
        assertEquals(11L, result.get(1).getProductId());
        // default limit applies
        assertEquals(AnalyticsReportService.DEFAULT_TOP_N, 5);
    }

    @Test
    void getProductMetrics_rejectsUnknownSortKey() {
        assertThrows(BadRequestException.class,
                () -> service.getProductMetrics(FROM, TO, "price", null));
    }

    @Test
    void getRevenueReport_computesGrossRefundedAndNet() {
        when(orderRevenueRepository.findByPaidAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        revenue("COMPLETED", "100.00"),
                        revenue("COMPLETED", "50.00"),
                        revenue("REFUNDED", "30.00"),
                        revenue("FAILED", "20.00")));

        RevenueReportDTO report = service.getRevenueReport(FROM, TO);

        assertEquals(0, new BigDecimal("150.00").compareTo(report.getGrossRevenue()));
        assertEquals(0, new BigDecimal("30.00").compareTo(report.getRefundedAmount()));
        assertEquals(0, new BigDecimal("120.00").compareTo(report.getNetRevenue()));
        assertEquals(2L, report.getCompletedPayments());
        assertEquals(1L, report.getRefundedPayments());
        assertEquals(FROM, report.getFrom());
        assertEquals(TO, report.getTo());
    }

    @Test
    void getCustomerMetrics_buildsCountsAndTopCustomers() {
        when(orderFactRepository.countCustomersAndOrders(FROM, TO))
                .thenReturn(Collections.singletonList(new Object[]{2L, 3L}));
        when(orderFactRepository.findTopCustomersByOrderedAmount(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(
                        new Object[]{7L, new BigDecimal("250.00"), 2L},
                        new Object[]{8L, new BigDecimal("100.00"), 1L}));

        CustomerMetricsDTO metrics = service.getCustomerMetrics(FROM, TO, 2);

        assertEquals(2L, metrics.getDistinctCustomers());
        assertEquals(3L, metrics.getTotalOrders());
        assertEquals(2, metrics.getTopCustomers().size());
        assertEquals(7L, metrics.getTopCustomers().get(0).getCustomerId());
        assertEquals(0, new BigDecimal("250.00").compareTo(metrics.getTopCustomers().get(0).getTotalOrderedAmount()));
    }

    @Test
    void getFulfillmentMetrics_mapsAveragesAndCounts() {
        when(fulfillmentMetricRepository.aggregateDurations(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(new Object[]{60.0, 300.0, 2L, 1L, 3L}));

        FulfillmentMetricsDTO metrics = service.getFulfillmentMetrics(FROM, TO);

        assertEquals(60.0, metrics.getAvgOrderToShipSeconds());
        assertEquals(300.0, metrics.getAvgOrderToDeliverSeconds());
        assertEquals(2L, metrics.getShippedOrders());
        assertEquals(1L, metrics.getDeliveredOrders());
        assertEquals(3L, metrics.getTrackedOrders());
    }

    @Test
    void getFulfillmentMetrics_handlesEmptyWindow() {
        when(fulfillmentMetricRepository.aggregateDurations(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(new Object[]{null, null, 0L, 0L, 0L}));

        FulfillmentMetricsDTO metrics = service.getFulfillmentMetrics(FROM, TO);

        assertNull(metrics.getAvgOrderToShipSeconds());
        assertEquals(0L, metrics.getTrackedOrders());
    }

    @Test
    void getSummary_computesLifetimeKpis() {
        when(dailyMetricRepository.sumLifetimeTotals())
                .thenReturn(Collections.singletonList(new Object[]{10L, new BigDecimal("1000.00"), 8L, 1L}));
        when(orderFactRepository.countDistinctCustomersLifetime()).thenReturn(7L);
        when(productMetricRepository.aggregateByProductLifetime()).thenReturn(List.of(
                new Object[]{11L, 5L, new BigDecimal("500.00"), 3L},
                new Object[]{12L, 9L, new BigDecimal("300.00"), 2L}));

        AnalyticsSummaryDTO summary = service.getSummary();

        assertEquals(10L, summary.getTotalOrders());
        assertEquals(0, new BigDecimal("1000.00").compareTo(summary.getTotalRevenue()));
        assertEquals(8L, summary.getCompletedOrders());
        assertEquals(1L, summary.getFailedOrders());
        // AOV = completed revenue / completed orders = 1000 / 8
        assertEquals(0, new BigDecimal("125.00").compareTo(summary.getAvgOrderValue()));
        assertEquals(7L, summary.getDistinctCustomers());
        assertEquals(2, summary.getTopProducts().size());
        assertEquals(11L, summary.getTopProducts().get(0).getProductId());
    }

    private OrderRevenue revenue(String status, String amount) {
        return OrderRevenue.builder()
                .orderId(1L)
                .amount(new BigDecimal(amount))
                .paymentStatus(status)
                .paidAt(LocalDateTime.of(2026, 8, 2, 12, 0))
                .build();
    }
}
