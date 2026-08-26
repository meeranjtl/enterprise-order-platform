package com.enterprise.order.gateway.controller;

import com.enterprise.order.gateway.config.DownstreamServiceProperties;
import com.enterprise.order.gateway.dto.ServiceHealthStatus;
import com.enterprise.order.gateway.dto.SystemHealthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Aggregates per-service health for the System Health UI page (Phase 13). No existing
 * gateway route forwards {@code /actuator/**} to a downstream service, and adding 8 of
 * them (one per service, matching the 5-places route-wiring ritual each) would be a lot
 * of surface for a page that only ever reads status — a dedicated aggregator that calls
 * each service's actuator endpoint directly via WebClient is simpler and keeps this
 * concern out of Gateway's real routing table entirely.
 */
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemHealthController {

    private final WebClient healthWebClient;
    private final DownstreamServiceProperties downstreamServiceProperties;

    @GetMapping("/health")
    public Mono<SystemHealthResponse> health() {
        Map<String, String> services = downstreamServiceProperties.getDownstreamServices();

        List<Mono<ServiceHealthStatus>> checks = services.entrySet().stream()
                .map(entry -> checkService(entry.getKey(), entry.getValue()))
                .toList();

        return Flux.merge(checks)
                .collectList()
                .map(results -> new SystemHealthResponse("UP", results, LocalDateTime.now()));
    }

    private Mono<ServiceHealthStatus> checkService(String name, String baseUrl) {
        return healthWebClient.get()
                .uri(baseUrl + "/actuator/health")
                .retrieve()
                .bodyToMono(Map.class)
                .map(body -> {
                    Object status = body.get("status");
                    return "UP".equals(status)
                            ? ServiceHealthStatus.up(name)
                            : ServiceHealthStatus.down(name, String.valueOf(status));
                })
                .onErrorResume(ex -> Mono.just(ServiceHealthStatus.down(name, ex.getMessage())));
    }
}
