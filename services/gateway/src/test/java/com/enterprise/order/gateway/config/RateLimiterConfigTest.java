package com.enterprise.order.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

class RateLimiterConfigTest {

    private RateLimiterConfig config;
    private KeyResolver resolver;

    @BeforeEach
    void setUp() {
        config = new RateLimiterConfig();
        resolver = config.ipKeyResolver();
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

    @Test
    void jwtKeyResolver_resolvesToAuthenticationSubjectWhenAuthenticated() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders"));

        var authentication = new TestingAuthenticationToken("42", null);
        var context = ReactiveSecurityContextHolder.withSecurityContext(
                reactor.core.publisher.Mono.just(new SecurityContextImpl(authentication)));

        StepVerifier.create(config.jwtKeyResolver().resolve(exchange).contextWrite(context))
                .expectNext("42")
                .verifyComplete();
    }

    @Test
    void jwtKeyResolver_fallsBackToIpWhenUnauthenticated() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/login")
                        .remoteAddress(new InetSocketAddress("10.0.0.9", 54321)));

        StepVerifier.create(config.jwtKeyResolver().resolve(exchange))
                .expectNext("10.0.0.9")
                .verifyComplete();
    }
}
