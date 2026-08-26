package com.enterprise.order.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Rate limiting key resolution for the {@code RequestRateLimiter} gateway filter.
 * <p>
 * Phase 4 limited per client IP (honouring {@code X-Forwarded-For} when a proxy sets it).
 * Phase 12 adds a per-user resolver based on the JWT subject, used as the default now that
 * requests carry a token; {@link #ipKeyResolver()} is kept for unauthenticated routes
 * (e.g. {@code /api/auth/**}) where there's no subject to key on.
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Resolves the rate-limit key to the client IP. Never returns an empty key —
     * {@code denyEmptyKey} (default true) would otherwise reject unresolvable clients with 429.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(resolveIp(exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"),
                exchange.getRequest().getRemoteAddress()));
    }

    /**
     * Resolves the rate-limit key to the authenticated JWT's subject (customer id), falling
     * back to client IP for requests with no token (the {@code /api/auth/**} routes, which
     * are permitted through without authentication) so unauthenticated traffic is still limited.
     */
    @Bean
    @Primary
    public KeyResolver jwtKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .switchIfEmpty(Mono.just(resolveIp(exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"),
                        exchange.getRequest().getRemoteAddress())));
    }

    private String resolveIp(String forwardedFor, InetSocketAddress remoteAddress) {
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return (remoteAddress != null && remoteAddress.getAddress() != null)
                ? remoteAddress.getAddress().getHostAddress()
                : "unknown";
    }
}
