package com.enterprise.order.analytics.controller;

import com.enterprise.order.analytics.dto.AnalyticsSummaryDTO;
import com.enterprise.order.analytics.dto.CustomerMetricsDTO;
import com.enterprise.order.analytics.dto.DailyMetricDTO;
import com.enterprise.order.analytics.dto.FulfillmentMetricsDTO;
import com.enterprise.order.analytics.dto.ProductPerformanceDTO;
import com.enterprise.order.analytics.dto.RevenueReportDTO;
import com.enterprise.order.analytics.dto.TopCustomerDTO;
import com.enterprise.order.analytics.service.AnalyticsReportService;
import com.enterprise.order.shared.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice test: only the MVC layer boots (no Kafka/JPA beans). This works
 * because JPA scanning lives in config.JpaConfig, which web slices skip, and
 * the shared-library GlobalExceptionHandler IS picked up (RestControllerAdvice)
 * so error paths render as BaseResponse.error.
 */
@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 15);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsReportService analyticsReportService;

    @Test
    void getDailyMetrics_returnsSeries() throws Exception {
        when(analyticsReportService.getDailyMetrics(eq(FROM), eq(TO))).thenReturn(List.of(
                DailyMetricDTO.builder()
                        .metricDate(FROM)
                        .totalOrders(3L)
                        .totalRevenue(BigDecimal.valueOf(300.0))
                        .completedOrders(2L)
                        .build()));

        mockMvc.perform(get("/api/v1/analytics/daily-metrics")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].metricDate", is(FROM.toString())))
                .andExpect(jsonPath("$.data[0].totalOrders", is(3)));
    }

    @Test
    void getProductMetrics_passesSortAndLimitThrough() throws Exception {
        when(analyticsReportService.getProductMetrics(any(), any(), any(), any()))
                .thenReturn(List.of(ProductPerformanceDTO.builder()
                        .productId(12L)
                        .unitsSold(9L)
                        .revenue(BigDecimal.valueOf(300.0))
                        .build()));

        mockMvc.perform(get("/api/v1/analytics/product-metrics")
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .param("sortBy", "units")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productId", is(12)))
                .andExpect(jsonPath("$.data[0].unitsSold", is(9)));

        verify(analyticsReportService).getProductMetrics(FROM, TO, "units", 3);
    }

    @Test
    void getRevenueReport_returnsNetRevenue() throws Exception {
        when(analyticsReportService.getRevenueReport(FROM, TO)).thenReturn(RevenueReportDTO.builder()
                .from(FROM)
                .to(TO)
                .grossRevenue(BigDecimal.valueOf(150.0))
                .refundedAmount(BigDecimal.valueOf(30.0))
                .netRevenue(BigDecimal.valueOf(120.0))
                .completedPayments(2L)
                .refundedPayments(1L)
                .build());

        mockMvc.perform(get("/api/v1/analytics/revenue")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grossRevenue", is(150.0)))
                .andExpect(jsonPath("$.data.netRevenue", is(120.0)))
                .andExpect(jsonPath("$.data.completedPayments", is(2)));
    }

    @Test
    void getCustomerMetrics_returnsCountsAndTopCustomers() throws Exception {
        when(analyticsReportService.getCustomerMetrics(eq(FROM), eq(TO), any()))
                .thenReturn(CustomerMetricsDTO.builder()
                        .from(FROM)
                        .to(TO)
                        .distinctCustomers(2L)
                        .totalOrders(3L)
                        .topCustomers(List.of(TopCustomerDTO.builder()
                                .customerId(7L)
                                .totalOrderedAmount(BigDecimal.valueOf(250.0))
                                .orderCount(2L)
                                .build()))
                        .build());

        mockMvc.perform(get("/api/v1/analytics/customer-metrics")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.distinctCustomers", is(2)))
                .andExpect(jsonPath("$.data.topCustomers[0].customerId", is(7)));
    }

    @Test
    void getFulfillmentMetrics_returnsAverages() throws Exception {
        when(analyticsReportService.getFulfillmentMetrics(FROM, TO))
                .thenReturn(FulfillmentMetricsDTO.builder()
                        .from(FROM)
                        .to(TO)
                        .trackedOrders(3L)
                        .shippedOrders(2L)
                        .deliveredOrders(1L)
                        .avgOrderToShipSeconds(60.0)
                        .avgOrderToDeliverSeconds(300.0)
                        .build());

        mockMvc.perform(get("/api/v1/analytics/fulfillment-metrics")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avgOrderToShipSeconds", is(60.0)))
                .andExpect(jsonPath("$.data.deliveredOrders", is(1)));
    }

    @Test
    void getSummary_returnsLifetimeKpis() throws Exception {
        when(analyticsReportService.getSummary()).thenReturn(AnalyticsSummaryDTO.builder()
                .totalOrders(10L)
                .totalRevenue(BigDecimal.valueOf(1000.0))
                .completedOrders(8L)
                .failedOrders(1L)
                .avgOrderValue(BigDecimal.valueOf(125.0))
                .distinctCustomers(7L)
                .topProducts(List.of())
                .build());

        mockMvc.perform(get("/api/v1/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders", is(10)))
                .andExpect(jsonPath("$.data.avgOrderValue", is(125.0)))
                .andExpect(jsonPath("$.data.distinctCustomers", is(7)));
    }

    @Test
    void invalidRange_returns400WithErrorResponse() throws Exception {
        when(analyticsReportService.getDailyMetrics(any(), any()))
                .thenThrow(new BadRequestException("'from' must not be after 'to'"));

        mockMvc.perform(get("/api/v1/analytics/daily-metrics")
                        .param("from", TO.toString())
                        .param("to", FROM.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("BAD_REQUEST")));
    }
}
