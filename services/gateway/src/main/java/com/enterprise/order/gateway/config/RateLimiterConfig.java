package com.enterprise.order.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Rate limiting key resolution for the {@code RequestRateLimiter} gateway filter.
 * <p>
 * Phase 4 limits per client IP (honouring {@code X-Forwarded-For} when a proxy sets it).
 * A per-user KeyResolver (based on the JWT subject) will be added alongside JWT auth
 * in Phase 12.
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Resolves the rate-limit key to the client IP. Never returns an empty key —
     * {@code denyEmptyKey} (default true) would otherwise reject unresolvable clients with 429.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String first = forwarded.split(",")[0].trim();
                if (!first.isEmpty()) {
                    return Mono.just(first);
                }
            }
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            String ip = (remoteAddress != null && remoteAddress.getAddress() != null)
                    ? remoteAddress.getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(ip);
        };
    }
}
