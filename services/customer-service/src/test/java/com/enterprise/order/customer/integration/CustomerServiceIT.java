package com.enterprise.order.customer.integration;

import com.enterprise.order.customer.dto.CustomerDTO;
import com.enterprise.order.customer.entity.Customer;
import com.enterprise.order.customer.repository.CustomerRepository;
import com.enterprise.order.customer.service.CustomerService;
import com.enterprise.order.shared.dto.AddressDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class CustomerServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("enterprise_order_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
    }

    @Test
    void testCreateCustomer_Integration() {
        AddressDTO address = AddressDTO.builder()
                .city("New York")
                .country("USA")
                .state("NY")
                .zipCode("10001")
                .build();

        CustomerDTO customerDTO = CustomerDTO.builder()
                .email("integration@example.com")
                .firstName("Integration")
                .lastName("Test")
                .phone("+1-555-0100")
                .address(address)
                .build();

        CustomerDTO created = customerService.createCustomer(customerDTO);

        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("integration@example.com", created.getEmail());
        assertEquals("ACTIVE", created.getStatus());
    }

    @Test
    void testGetCustomer_Integration() {
        Customer customer = Customer.builder()
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .city("Boston")
                .country("USA")
                .status(Customer.CustomerStatus.ACTIVE)
                .build();

        Customer saved = customerRepository.save(customer);
        CustomerDTO result = customerService.getCustomer(saved.getId());

        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
        assertEquals("Test", result.getFirstName());
    }

    @Test
    void testUpdateCustomer_Integration() {
        Customer customer = Customer.builder()
                .email("update@example.com")
                .firstName("Update")
                .lastName("Test")
                .city("Chicago")
                .country("USA")
                .status(Customer.CustomerStatus.ACTIVE)
                .build();

        Customer saved = customerRepository.save(customer);

        AddressDTO newAddress = AddressDTO.builder()
                .city("Los Angeles")
                .country("USA")
                .build();

        CustomerDTO updateDTO = CustomerDTO.builder()
                .email("update@example.com")
                .firstName("Updated")
                .lastName("Test")
                .address(newAddress)
                .build();

        CustomerDTO result = customerService.updateCustomer(saved.getId(), updateDTO);

        assertNotNull(result);
        assertEquals("Updated", result.getFirstName());
    }

    @Test
    void testDeleteCustomer_Integration() {
        Customer customer = Customer.builder()
                .email("delete@example.com")
                .firstName("Delete")
                .lastName("Test")
                .status(Customer.CustomerStatus.ACTIVE)
                .build();

        Customer saved = customerRepository.save(customer);
        customerService.deleteCustomer(saved.getId());

        assertFalse(customerRepository.existsById(saved.getId()));
    }
}

