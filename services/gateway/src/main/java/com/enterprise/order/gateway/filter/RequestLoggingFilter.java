package com.enterprise.order.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Structured access log for every routed request: method + path on entry,
 * method + path + status + duration on completion.
 * <p>
 * The correlation ID is placed in the MDC explicitly around each log statement
 * (reactor may hop threads, so thread-locals alone are unreliable).
 * Response bodies are intentionally not logged — capturing them would require
 * buffering the stream, which is deferred as low-value complexity.
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final String START_TIME_ATTR = "requestStartTime";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getAttributes().put(START_TIME_ATTR, System.nanoTime());

        String method = String.valueOf(exchange.getRequest().getMethod());
        String path = exchange.getRequest().getPath().value();
        String correlationId = exchange.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTR);

        logWithCorrelation(correlationId, () ->
                log.info("Incoming request: {} {}", method, path));

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            Long startTime = exchange.getAttribute(START_TIME_ATTR);
            long durationMs = startTime != null ? (System.nanoTime() - startTime) / 1_000_000 : -1;
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            logWithCorrelation(correlationId, () ->
                    log.info("Completed request: {} {} -> {} in {} ms",
                            method, path, status != null ? status.value() : "n/a", durationMs));
        }));
    }

    private void logWithCorrelation(String correlationId, Runnable logStatement) {
        if (correlationId != null) {
            MDC.put(CorrelationIdFilter.CORRELATION_ID_ATTR, correlationId);
        }
        try {
            logStatement.run();
        } finally {
            MDC.remove(CorrelationIdFilter.CORRELATION_ID_ATTR);
        }
    }
}
