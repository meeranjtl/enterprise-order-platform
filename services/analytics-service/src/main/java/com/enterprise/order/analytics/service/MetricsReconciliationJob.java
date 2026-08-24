package com.enterprise.order.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Periodic reconciliation of the analytics rollups (Phase 10).
 *
 * Guarantees eventual correctness even when concurrent consumer transactions
 * interleave (see MetricsAggregationService.reconcileRecentMetrics). Cheap by
 * design: it only touches dates that actually have order facts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsReconciliationJob {

    private final MetricsAggregationService metricsAggregationService;

    @Value("${analytics.reconcile.lookback-days:3}")
    private int lookbackDays;

    @Scheduled(fixedDelayString = "${analytics.reconcile.interval-ms:10000}")
    public void reconcile() {
        try {
            metricsAggregationService.reconcileRecentMetrics(LocalDate.now(), lookbackDays);
        } catch (Exception e) {
            log.error("Analytics reconciliation sweep failed", e);
        }
    }
}
