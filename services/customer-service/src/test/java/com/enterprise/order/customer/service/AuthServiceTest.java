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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Claims claims;

    @InjectMocks
    private AuthService authService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = Customer.builder()
                .id(1L)
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .status(Customer.CustomerStatus.ACTIVE)
                .role(Customer.Role.CUSTOMER)
                .password("hashed-password")
                .build();
    }

    @Test
    void register_createsCustomerAsCustomerRole_andReturnsTokens() {
        RegisterRequest request = RegisterRequest.builder()
                .email("new@example.com")
                .password("password123")
                .firstName("New")
                .lastName("User")
                .build();

        when(customerRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenValiditySeconds()).thenReturn(900L);
        when(jwtTokenProvider.getRefreshTokenValidityMillis()).thenReturn(604800000L);

        TokenResponse response = authService.register(request);

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());

        verify(customerRepository, times(2)).save(argThat(c -> c.getRole() == Customer.Role.CUSTOMER));
    }

    @Test
    void register_rejectsDuplicateEmail() {
        RegisterRequest request = RegisterRequest.builder()
                .email("john@example.com")
                .password("password123")
                .firstName("John")
                .build();

        when(customerRepository.findByEmail("john@example.com")).thenReturn(Optional.of(customer));

        assertThrows(ConflictException.class, () -> authService.register(request));
        verify(customerRepository, never()).save(any());
    }

    @Test
    void login_succeedsWithCorrectPassword() {
        LoginRequest request = LoginRequest.builder().email("john@example.com").password("correct").build();

        when(customerRepository.findByEmail("john@example.com")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("correct", "hashed-password")).thenReturn(true);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenValiditySeconds()).thenReturn(900L);
        when(jwtTokenProvider.getRefreshTokenValidityMillis()).thenReturn(604800000L);

        TokenResponse response = authService.login(request);

        assertEquals("access-token", response.getAccessToken());
    }

    @Test
    void login_rejectsWrongPassword() {
        LoginRequest request = LoginRequest.builder().email("john@example.com").password("wrong").build();

        when(customerRepository.findByEmail("john@example.com")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.login(request));
    }

    @Test
    void login_rejectsUnknownEmail() {
        LoginRequest request = LoginRequest.builder().email("nobody@example.com").password("x").build();

        when(customerRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.login(request));
    }

    @Test
    void refresh_rejectsTokenThatIsNotRefreshType() {
        RefreshRequest request = RefreshRequest.builder().refreshToken("some-access-token").build();

        when(jwtTokenProvider.parseClaims("some-access-token")).thenReturn(claims);
        when(jwtTokenProvider.isRefreshToken(claims)).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.refresh(request));
        verify(customerRepository, never()).findById(any());
    }

    @Test
    void refresh_rejectsExpiredOrMalformedToken() {
        RefreshRequest request = RefreshRequest.builder().refreshToken("garbage").build();

        when(jwtTokenProvider.parseClaims("garbage")).thenThrow(mock(JwtException.class));

        assertThrows(UnauthorizedException.class, () -> authService.refresh(request));
    }

    @Test
    void refresh_rejectsTokenThatDoesNotMatchStoredHash() {
        String rawToken = "presented-refresh-token";
        RefreshRequest request = RefreshRequest.builder().refreshToken(rawToken).build();

        customer.setRefreshTokenHash("a-different-hash-entirely");
        customer.setRefreshTokenExpiresAt(LocalDateTime.now().plusDays(1));

        when(jwtTokenProvider.parseClaims(rawToken)).thenReturn(claims);
        when(jwtTokenProvider.isRefreshToken(claims)).thenReturn(true);
        when(claims.getSubject()).thenReturn("1");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        assertThrows(UnauthorizedException.class, () -> authService.refresh(request));
    }

    @Test
    void refresh_rejectsWhenNoRefreshTokenIsOnFile() {
        // e.g. the customer already logged out
        RefreshRequest request = RefreshRequest.builder().refreshToken("stale-token").build();

        when(jwtTokenProvider.parseClaims("stale-token")).thenReturn(claims);
        when(jwtTokenProvider.isRefreshToken(claims)).thenReturn(true);
        when(claims.getSubject()).thenReturn("1");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        assertThrows(UnauthorizedException.class, () -> authService.refresh(request));
    }

    @Test
    void logout_clearsStoredRefreshToken() {
        RefreshRequest request = RefreshRequest.builder().refreshToken("a-refresh-token").build();

        customer.setRefreshTokenHash("some-hash");
        customer.setRefreshTokenExpiresAt(LocalDateTime.now().plusDays(1));

        when(jwtTokenProvider.parseClaims("a-refresh-token")).thenReturn(claims);
        when(jwtTokenProvider.isRefreshToken(claims)).thenReturn(true);
        when(claims.getSubject()).thenReturn("1");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.logout(request);

        assertNull(customer.getRefreshTokenHash());
        assertNull(customer.getRefreshTokenExpiresAt());
        verify(customerRepository).save(customer);
    }
}
