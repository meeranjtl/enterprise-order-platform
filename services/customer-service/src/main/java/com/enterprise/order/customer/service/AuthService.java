package com.enterprise.order.customer.service;

import com.enterprise.order.customer.dto.LoginRequest;
import com.enterprise.order.customer.dto.RefreshRequest;
import com.enterprise.order.customer.dto.RegisterRequest;
import com.enterprise.order.customer.dto.TokenResponse;
import com.enterprise.order.customer.entity.Customer;
import com.enterprise.order.customer.repository.CustomerRepository;
import com.enterprise.order.customer.security.JwtTokenProvider;
import com.enterprise.order.shared.exception.ConflictException;
import com.enterprise.order.shared.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final CustomerRepository customerRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public TokenResponse register(RegisterRequest request) {
        log.info("Registering new customer with email: {}", request.getEmail());

        if (customerRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ConflictException("Customer with email " + request.getEmail() + " already exists");
        }

        // Role is never taken from the request — self-registration is always CUSTOMER.
        Customer customer = Customer.builder()
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .status(Customer.CustomerStatus.ACTIVE)
                .role(Customer.Role.CUSTOMER)
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        Customer saved = customerRepository.save(customer);
        log.info("Customer registered with id: {}", saved.getId());

        return issueTokens(saved);
    }

    public TokenResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        Customer customer = customerRepository.findByEmail(request.getEmail())
                .filter(c -> c.getPassword() != null
                        && passwordEncoder.matches(request.getPassword(), c.getPassword()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        return issueTokens(customer);
    }

    public TokenResponse refresh(RefreshRequest request) {
        Claims claims = parseRefreshTokenOrThrow(request.getRefreshToken());
        Customer customer = loadCustomerFromSubject(claims);

        if (customer.getRefreshTokenHash() == null
                || customer.getRefreshTokenExpiresAt() == null
                || customer.getRefreshTokenExpiresAt().isBefore(LocalDateTime.now())
                || !customer.getRefreshTokenHash().equals(hashToken(request.getRefreshToken()))) {
            throw new UnauthorizedException("Refresh token is invalid, expired, or has been revoked");
        }

        log.info("Refreshing tokens for customer id: {}", customer.getId());
        return issueTokens(customer);
    }

    public void logout(RefreshRequest request) {
        Claims claims = parseRefreshTokenOrThrow(request.getRefreshToken());
        Customer customer = loadCustomerFromSubject(claims);

        log.info("Logging out customer id: {}", customer.getId());
        customer.setRefreshTokenHash(null);
        customer.setRefreshTokenExpiresAt(null);
        customerRepository.save(customer);
    }

    private TokenResponse issueTokens(Customer customer) {
        String accessToken = jwtTokenProvider.generateAccessToken(customer);
        String refreshToken = jwtTokenProvider.generateRefreshToken(customer);

        customer.setRefreshTokenHash(hashToken(refreshToken));
        customer.setRefreshTokenExpiresAt(LocalDateTime.ofInstant(
                Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenValidityMillis()), ZoneId.systemDefault()));
        customerRepository.save(customer);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenValiditySeconds())
                .build();
    }

    private Claims parseRefreshTokenOrThrow(String refreshToken) {
        try {
            Claims claims = jwtTokenProvider.parseClaims(refreshToken);
            if (!jwtTokenProvider.isRefreshToken(claims)) {
                throw new UnauthorizedException("Token is not a refresh token");
            }
            return claims;
        } catch (JwtException e) {
            throw new UnauthorizedException("Refresh token is invalid or expired");
        }
    }

    private Customer loadCustomerFromSubject(Claims claims) {
        Long customerId = Long.valueOf(claims.getSubject());
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid or expired"));
    }

    /**
     * SHA-256, not BCrypt: refresh tokens are already high-entropy random strings (not
     * user-chosen passwords) and are well over BCrypt's 72-byte input limit, which would
     * silently truncate and weaken the comparison. A fixed-length digest is the standard
     * approach for hashing tokens before storage.
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
