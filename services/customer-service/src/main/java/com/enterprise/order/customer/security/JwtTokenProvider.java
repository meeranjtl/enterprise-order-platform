package com.enterprise.order.customer.security;

import com.enterprise.order.customer.entity.Customer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Issues and parses the platform's JWTs. This is customer-service's own token
 * utility (independent of shared-library's {@code JwtDecoder}, which every
 * service — including this one, via {@code SecurityConfig} — uses only to
 * authenticate incoming requests). Kept separate because refresh tokens need
 * to be parsed here during {@code /api/auth/refresh}, and the shared decoder
 * deliberately rejects anything that isn't an access token.
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private static final long ACCESS_TOKEN_VALIDITY_MS = 15 * 60 * 1000L;
    private static final long REFRESH_TOKEN_VALIDITY_MS = 7 * 24 * 60 * 60 * 1000L;

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey key;

    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Customer customer) {
        return buildToken(customer, TYPE_ACCESS, ACCESS_TOKEN_VALIDITY_MS);
    }

    public String generateRefreshToken(Customer customer) {
        return buildToken(customer, TYPE_REFRESH, REFRESH_TOKEN_VALIDITY_MS);
    }

    public long getAccessTokenValiditySeconds() {
        return ACCESS_TOKEN_VALIDITY_MS / 1000;
    }

    public long getRefreshTokenValidityMillis() {
        return REFRESH_TOKEN_VALIDITY_MS;
    }

    /**
     * Parses and verifies a token's signature and expiry. Throws an unchecked
     * {@code io.jsonwebtoken.JwtException} (or subclass, e.g. {@code ExpiredJwtException})
     * if invalid — callers translate that into a 401.
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    private String buildToken(Customer customer, String type, long validityMs) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(customer.getId()))
                .claim(CLAIM_ROLES, List.of(customer.getRole().name()))
                .claim(CLAIM_TYPE, type)
                .claim("email", customer.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(validityMs)))
                // Explicit algorithm — signWith(key) alone auto-picks the strongest HMAC
                // variant the key length supports (HS384/HS512 for a long secret), but every
                // decoder (shared-library's JwtDecoderConfig, gateway's reactive equivalent)
                // is built via NimbusJwtDecoder.withSecretKey(key), which defaults to
                // expecting HS256 specifically. A mismatch here fails signature verification
                // silently (401 on every protected endpoint) even though the token looks fine.
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
