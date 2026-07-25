package com.enterprise.order.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

class RateLimiterConfigTest {

    private KeyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new RateLimiterConfig().ipKeyResolver();
    }

    @Test
    void resolvesKeyFromRemoteAddress() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/customers")
                        .remoteAddress(new InetSocketAddress("10.0.0.5", 54321)));

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("10.0.0.5")
                .verifyComplete();
    }

    @Test
    void prefersXForwardedForOverRemoteAddress() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/customers")
                        .remoteAddress(new InetSocketAddress("10.0.0.5", 54321))
                        .header("X-Forwarded-For", "203.0.113.7"));

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("203.0.113.7")
                .verifyComplete();
    }

    @Test
    void usesFirstIpFromCommaSeparatedForwardedHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/customers")
                        .header("X-Forwarded-For", "1.2.3.4, 5.6.7.8"));

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("1.2.3.4")
                .verifyComplete();
    }

    @Test
    void trimsWhitespaceAroundForwardedIp() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/customers")
                        .header("X-Forwarded-For", "  9.9.9.9 , 1.1.1.1"));

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("9.9.9.9")
                .verifyComplete();
    }

    @Test
    void fallsBackToUnknownWhenNoRemoteAddressAndNoHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/customers"));

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("unknown")
                .verifyComplete();
    }

    @Test
    void fallsBackToRemoteAddressWhenForwardedHeaderBlank() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/customers")
                        .remoteAddress(new InetSocketAddress("10.1.2.3", 54321))
                        .header("X-Forwarded-For", "  "));

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("10.1.2.3")
                .verifyComplete();
    }
}
