package com.enterprise.order.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;
    private GatewayFilterChain chain;
    private final AtomicReference<ServerWebExchange> downstreamExchange = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            downstreamExchange.set(invocation.getArgument(0));
            return Mono.empty();
        });
    }

    @Test
    void generatesCorrelationIdWhenHeaderAbsent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/customers"));

        filter.filter(exchange, chain).block();

        String responseHeader = exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(responseHeader).isNotBlank();
        UUID.fromString(responseHeader); // throws if not a valid UUID
    }

    @Test
    void propagatesExistingCorrelationId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/customers")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "existing-id-123"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo("existing-id-123");
        assertThat(downstreamExchange.get().getRequest().getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo("existing-id-123");
    }

    @Test
    void generatesNewIdWhenHeaderBlank() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/customers")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "   "));

        filter.filter(exchange, chain).block();

        String responseHeader = exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(responseHeader).isNotBlank().isNotEqualTo("   ");
        UUID.fromString(responseHeader);
    }

    @Test
    void storesCorrelationIdAsExchangeAttribute() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/customers")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "attr-check"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.<String>getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTR))
                .isEqualTo("attr-check");
    }

    @Test
    void addsCorrelationIdToDownstreamRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/products"));

        filter.filter(exchange, chain).block();

        String downstreamHeader = downstreamExchange.get().getRequest().getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(downstreamHeader).isNotBlank();
        UUID.fromString(downstreamHeader);
    }
}
