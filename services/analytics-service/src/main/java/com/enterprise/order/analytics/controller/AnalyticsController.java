package com.enterprise.order.analytics.controller;

import com.enterprise.order.analytics.dto.AnalyticsSummaryDTO;
import com.enterprise.order.analytics.dto.CustomerMetricsDTO;
import com.enterprise.order.analytics.dto.DailyMetricDTO;
import com.enterprise.order.analytics.dto.FulfillmentMetricsDTO;
import com.enterprise.order.analytics.dto.ProductPerformanceDTO;
import com.enterprise.order.analytics.dto.RevenueReportDTO;
import com.enterprise.order.analytics.service.AnalyticsReportService;
import com.enterprise.order.shared.dto.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Business metrics and reporting APIs")
public class AnalyticsController {

    private final AnalyticsReportService analyticsReportService;

    @GetMapping("/daily-metrics")
    @Operation(summary = "Daily metric series for a date range")
    public ResponseEntity<BaseResponse<List<DailyMetricDTO>>> getDailyMetrics(
            @Parameter(description = "Start date yyyy-MM-dd; defaults to 'to' minus 29 days")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date yyyy-MM-dd; defaults to today")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.info("GET /api/v1/analytics/daily-metrics from={} to={}", from, to);

        List<DailyMetricDTO> metrics = analyticsReportService.getDailyMetrics(from, to);
        return ResponseEntity.ok(BaseResponse.success(metrics, "Daily metrics retrieved successfully"));
    }

    @GetMapping("/product-metrics")
    @Operation(summary = "Product performance over a date range (top-N)")
    public ResponseEntity<BaseResponse<List<ProductPerformanceDTO>>> getProductMetrics(
            @Parameter(description = "Start date yyyy-MM-dd; defaults to 'to' minus 29 days")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date yyyy-MM-dd; defaults to today")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Sort key: revenue (default) or units")
            @RequestParam(required = false, defaultValue = "revenue") String sortBy,
            @Parameter(description = "Max products to return; defaults to 5")
            @RequestParam(required = false) Integer limit) {
        log.info("GET /api/v1/analytics/product-metrics from={} to={} sortBy={} limit={}", from, to, sortBy, limit);

        List<ProductPerformanceDTO> metrics = analyticsReportService.getProductMetrics(from, to, sortBy, limit);
        return ResponseEntity.ok(BaseResponse.success(metrics, "Product metrics retrieved successfully"));
    }

    @GetMapping("/revenue")
    @Operation(summary = "Revenue report (gross, refunded, net) for a date range")
    public ResponseEntity<BaseResponse<RevenueReportDTO>> getRevenueReport(
            @Parameter(description = "Start date yyyy-MM-dd; defaults to 'to' minus 29 days")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date yyyy-MM-dd; defaults to today")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.info("GET /api/v1/analytics/revenue from={} to={}", from, to);

        RevenueReportDTO report = analyticsReportService.getRevenueReport(from, to);
        return ResponseEntity.ok(BaseResponse.success(report, "Revenue report retrieved successfully"));
    }

    @GetMapping("/customer-metrics")
    @Operation(summary = "Customer analytics for a date range (distinct customers, top spenders)")
    public ResponseEntity<BaseResponse<CustomerMetricsDTO>> getCustomerMetrics(
            @Parameter(description = "Start date yyyy-MM-dd; defaults to 'to' minus 29 days")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date yyyy-MM-dd; defaults to today")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Max top customers to return; defaults to 5")
            @RequestParam(required = false) Integer limit) {
        log.info("GET /api/v1/analytics/customer-metrics from={} to={} limit={}", from, to, limit);

        CustomerMetricsDTO metrics = analyticsReportService.getCustomerMetrics(from, to, limit);
        return ResponseEntity.ok(BaseResponse.success(metrics, "Customer metrics retrieved successfully"));
    }

    @GetMapping("/fulfillment-metrics")
    @Operation(summary = "Fulfillment timing averages for orders placed in the date range")
    public ResponseEntity<BaseResponse<FulfillmentMetricsDTO>> getFulfillmentMetrics(
            @Parameter(description = "Start date yyyy-MM-dd; defaults to 'to' minus 29 days")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date yyyy-MM-dd; defaults to today")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.info("GET /api/v1/analytics/fulfillment-metrics from={} to={}", from, to);

        FulfillmentMetricsDTO metrics = analyticsReportService.getFulfillmentMetrics(from, to);
        return ResponseEntity.ok(BaseResponse.success(metrics, "Fulfillment metrics retrieved successfully"));
    }

    @GetMapping("/summary")
    @Operation(summary = "Lifetime KPI snapshot across all aggregated data")
    public ResponseEntity<BaseResponse<AnalyticsSummaryDTO>> getSummary() {
        log.info("GET /api/v1/analytics/summary");

        AnalyticsSummaryDTO summary = analyticsReportService.getSummary();
        return ResponseEntity.ok(BaseResponse.success(summary, "Analytics summary retrieved successfully"));
    }
}
