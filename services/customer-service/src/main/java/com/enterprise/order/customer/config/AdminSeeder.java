package com.enterprise.order.customer.config;

import com.enterprise.order.customer.entity.Customer;
import com.enterprise.order.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds one ADMIN account on startup if none exists yet, for local
 * testing/Swagger use. Idempotent (checked by role, not run-once tracking) so
 * it's safe across restarts. Dev-only credentials; override via
 * {@code admin.seed.password} for anything beyond local use.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.seed.email:admin@enterprise-order.local}")
    private String adminEmail;

    @Value("${admin.seed.password:Admin123!}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (customerRepository.findByEmail(adminEmail).isPresent()) {
            return;
        }

        Customer admin = Customer.builder()
                .email(adminEmail)
                .firstName("Platform")
                .lastName("Admin")
                .status(Customer.CustomerStatus.ACTIVE)
                .role(Customer.Role.ADMIN)
                .password(passwordEncoder.encode(adminPassword))
                .build();

        customerRepository.save(admin);
        log.info("Seeded default admin account: {}", adminEmail);
    }
}
