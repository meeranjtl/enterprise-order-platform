package com.enterprise.order.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Single, app-wide CORS policy for the gateway. Previously this lived in
 * {@code spring.cloud.gateway.globalcors} (application.yml), but that config only
 * applies within Gateway's own routing engine — a plain {@code @RestController} living
 * in this app ({@code SystemHealthController}, Phase 13) is dispatched via WebFlux's
 * ordinary handler mapping, not a routed path, so it never saw those headers (confirmed
 * via a live preflight coming back with no {@code Access-Control-Allow-Origin} header).
 * <p>
 * The property {@code globalcors.add-to-simple-url-handler-mapping} looked like the
 * documented fix, but it registers a {@code CorsConfigurationSource} bean that Spring
 * Security's WebFlux auto-config detects and enforces itself — and once <i>any</i>
 * {@code CorsWebFilter}/{@code CorsConfigurationSource} bean exists in the context,
 * Security enforces CORS globally using it, 403-ing every path that bean's source
 * doesn't cover. That ruled out a narrowly-scoped filter (tried first, scoped only to
 * {@code /api/v1/system/**}) — it fixed that one path but 403'd every gateway-routed
 * request, since Security now denied anything the new source didn't explicitly match.
 * The only safe fix is one {@code CorsWebFilter} covering the whole app (mirroring the
 * old globalcors policy) with {@code spring.cloud.gateway.globalcors} removed entirely —
 * running both at once double-applies {@code Access-Control-Allow-*} headers, which
 * browsers reject as invalid. See gotchas.md.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id", "X-Requested-With", "Accept"));
        config.setExposedHeaders(List.of("X-Correlation-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
