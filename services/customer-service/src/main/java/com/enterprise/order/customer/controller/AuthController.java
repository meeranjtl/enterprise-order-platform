package com.enterprise.order.customer.controller;

import com.enterprise.order.customer.dto.LoginRequest;
import com.enterprise.order.customer.dto.RefreshRequest;
import com.enterprise.order.customer.dto.RegisterRequest;
import com.enterprise.order.customer.dto.TokenResponse;
import com.enterprise.order.customer.service.AuthService;
import com.enterprise.order.shared.dto.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration, login, token refresh, and logout")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new customer and receive a token pair")
    public ResponseEntity<BaseResponse<TokenResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /api/auth/register - email: {}", request.getEmail());

        TokenResponse tokens = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(tokens, "Registered successfully"));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and receive a token pair")
    public ResponseEntity<BaseResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/auth/login - email: {}", request.getEmail());

        TokenResponse tokens = authService.login(request);
        return ResponseEntity.ok(BaseResponse.success(tokens, "Login successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a valid refresh token for a new token pair")
    public ResponseEntity<BaseResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        log.info("POST /api/auth/refresh");

        TokenResponse tokens = authService.refresh(request);
        return ResponseEntity.ok(BaseResponse.success(tokens, "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token")
    public ResponseEntity<BaseResponse<Void>> logout(@Valid @RequestBody RefreshRequest request) {
        log.info("POST /api/auth/logout");

        authService.logout(request);
        return ResponseEntity.ok(BaseResponse.success(null, "Logged out successfully"));
    }
}
