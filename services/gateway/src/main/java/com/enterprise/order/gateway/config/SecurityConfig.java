package com.enterprise.order.gateway.config;

import com.enterprise.order.gateway.dto.GatewayErrorResponse;
import com.enterprise.order.gateway.filter.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Reactive equivalent of shared-library's {@code JwtDecoderConfig} + {@code SecurityConfig}.
 * Duplicated here rather than shared because the gateway is reactive (Netty) and cannot
 * depend on the servlet-based shared-library — same pattern as every other Phase 11/12
 * cross-cutting concern in this module. Validates the same HS256 tokens issued by
 * customer-service; the {@code /api/auth/**} route itself is permitted through so login/
 * register/refresh/logout reach customer-service without a token.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/**",
            "/api-docs/**",
            "/*/api-docs",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api/auth/**",
            "/fallback/**"
    };

    @Value("${jwt.secret:phase12-dev-only-shared-secret-change-in-production-32bytes-min}")
    private String secret;

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(key).build();

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                List.of(JwtValidators.createDefault(), accessTokenOnly()));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> accessTokenOnly() {
        return jwt -> {
            if (!"access".equals(jwt.getClaimAsString("type"))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "Refresh tokens cannot be used to access protected resources", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(this::handleUnauthorized)
                        .accessDeniedHandler(this::handleForbidden));
        return http.build();
    }

    private ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    private Mono<Void> handleUnauthorized(ServerWebExchange exchange, Exception ex) {
        return writeError(exchange, 401, "UNAUTHORIZED", "Authentication is required to access this resource");
    }

    private Mono<Void> handleForbidden(ServerWebExchange exchange, Exception ex) {
        return writeError(exchange, 403, "FORBIDDEN", "You do not have permission to access this resource");
    }

    private Mono<Void> writeError(ServerWebExchange exchange, int status, String code, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatusCode.valueOf(status));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Security runs ahead of gateway's own routing filters (incl. CorrelationIdFilter), so a
        // rejected request never reaches it — echo/generate the header here too, same as
        // GatewayExceptionHandler does for its own error paths, so every gateway-originated
        // response carries one.
        String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        response.getHeaders().set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(GatewayErrorResponse.of(code, message, null));
        } catch (Exception e) {
            bytes = "{\"success\":false}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
