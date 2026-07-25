package com.enterprise.order.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Generates (or propagates) a correlation ID for every request passing through the gateway.
 * <ul>
 *     <li>Reads {@value #CORRELATION_ID_HEADER} from the incoming request, generating a UUID when absent/blank</li>
 *     <li>Adds the header to the downstream request so services can log it</li>
 *     <li>Echoes the header on the response so callers can correlate their own logs</li>
 *     <li>Stores it as an exchange attribute (for other filters) and in the reactor context
 *         (for MDC propagation via {@code Hooks.enableAutomaticContextPropagation()})</li>
 * </ul>
 * Runs first in the filter chain so every downstream filter and service sees the ID.
 */
@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_ATTR = "correlationId";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);
        exchange.getAttributes().put(CORRELATION_ID_ATTR, correlationId);

        final String cid = correlationId;
        MDC.put(CORRELATION_ID_ATTR, cid);
        try {
            log.debug("Correlation ID assigned: {}", cid);
        } finally {
            MDC.remove(CORRELATION_ID_ATTR);
        }

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .contextWrite(ctx -> ctx.put(CORRELATION_ID_ATTR, cid));
    }
}
