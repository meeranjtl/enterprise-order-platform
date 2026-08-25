package com.enterprise.order.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Validates JWTs issued by customer-service against the platform-wide shared
 * secret (HS256). Used by every servlet service's {@link SecurityConfig} as an
 * OAuth2 resource server decoder — this is request-authentication only, not
 * token issuance (customer-service issues tokens separately via its own
 * JwtTokenProvider).
 * <p>
 * Rejects any token whose {@code type} claim isn't {@code "access"} — this is
 * what stops a refresh token from being used to call a protected endpoint.
 * customer-service's own refresh-token handling reads refresh tokens through a
 * separate path, not through this decoder.
 */
@Configuration
public class JwtDecoderConfig {

    // Default keeps services that do not declare jwt.secret bootable in isolation
    // (mirrors KafkaConfig's bootstrap-servers default); Docker Compose overrides
    // this via JWT_SECRET for every service, and every service's application.yml
    // also declares it explicitly for visibility.
    @Value("${jwt.secret:phase12-dev-only-shared-secret-change-in-production-32bytes-min}")
    private String secret;

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).build();

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
}
