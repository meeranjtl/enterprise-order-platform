package com.enterprise.order.customer.security;

import com.enterprise.order.customer.entity.Customer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private static final String SECRET = "test-only-secret-key-at-least-32-bytes-long-for-hs256";

    private JwtTokenProvider jwtTokenProvider;
    private Customer customer;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "secret", SECRET);
        ReflectionTestUtils.invokeMethod(jwtTokenProvider, "init");

        customer = Customer.builder()
                .id(42L)
                .email("john@example.com")
                .role(Customer.Role.CUSTOMER)
                .build();
    }

    @Test
    void generateAccessToken_carriesExpectedClaims() {
        String token = jwtTokenProvider.generateAccessToken(customer);
        Claims claims = jwtTokenProvider.parseClaims(token);

        assertEquals("42", claims.getSubject());
        assertEquals("access", claims.get("type"));
        assertEquals(List.of("CUSTOMER"), claims.get("roles"));
        assertFalse(jwtTokenProvider.isRefreshToken(claims));
    }

    /**
     * Regression test: signWith(key) alone (no explicit algorithm) picks HS384/HS512 for a
     * long secret, while every resource-server decoder in the platform is built via
     * NimbusJwtDecoder.withSecretKey(key), which defaults to expecting HS256 — a mismatch
     * here signs a token that verifies fine against itself (parseClaims uses the same key
     * material) but is silently rejected by every other service. Assert against the actual
     * decoder class the platform uses, not just this class's own round-trip.
     */
    @Test
    void generateAccessToken_isDecodableByThePlatformsActualResourceServerDecoder() {
        String token = jwtTokenProvider.generateAccessToken(customer);

        SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).build();

        Jwt jwt = decoder.decode(token);
        assertEquals("42", jwt.getSubject());
    }

    @Test
    void generateRefreshToken_isMarkedAsRefreshType() {
        String token = jwtTokenProvider.generateRefreshToken(customer);
        Claims claims = jwtTokenProvider.parseClaims(token);

        assertEquals("refresh", claims.get("type"));
        assertTrue(jwtTokenProvider.isRefreshToken(claims));
    }

    @Test
    void parseClaims_rejectsExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minusSeconds(3600);
        String expiredToken = Jwts.builder()
                .subject("42")
                .claim("type", "access")
                .issuedAt(Date.from(past.minusSeconds(60)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();

        assertThrows(ExpiredJwtException.class, () -> jwtTokenProvider.parseClaims(expiredToken));
    }

    @Test
    void parseClaims_rejectsTokenSignedWithDifferentSecret() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "a-completely-different-secret-that-is-also-32-bytes".getBytes(StandardCharsets.UTF_8));
        String badToken = Jwts.builder()
                .subject("42")
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(wrongKey)
                .compact();

        assertThrows(io.jsonwebtoken.security.SignatureException.class,
                () -> jwtTokenProvider.parseClaims(badToken));
    }
}
