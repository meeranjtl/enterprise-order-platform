package com.enterprise.order.customer.service;

import com.enterprise.order.customer.dto.CustomerDTO;
import com.enterprise.order.customer.entity.Customer;
import com.enterprise.order.customer.mapper.CustomerMapper;
import com.enterprise.order.customer.repository.CustomerRepository;
import com.enterprise.order.shared.exception.ConflictException;
import com.enterprise.order.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerMapper customerMapper;

    @InjectMocks
    private CustomerService customerService;

    private Customer customer;
    private CustomerDTO customerDTO;

    @BeforeEach
    void setUp() {
        customer = Customer.builder()
                .id(1L)
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .phone("+1-234-567-8900")
                .city("New York")
                .country("USA")
                .status(Customer.CustomerStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        customerDTO = CustomerDTO.builder()
                .id(1L)
                .email("john@example.com")
                .firstName("John")
                .lastName("Doe")
                .phone("+1-234-567-8900")
                .status("ACTIVE")
                .build();
    }

    @Test
    void testCreateCustomer_Success() {
        when(customerRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());
        when(customerMapper.toEntity(any(CustomerDTO.class))).thenReturn(customer);
        when(customerRepository.save(any(Customer.class))).thenReturn(customer);
        when(customerMapper.toDTO(any(Customer.class))).thenReturn(customerDTO);

        CustomerDTO result = customerService.createCustomer(customerDTO);

        assertNotNull(result);
        assertEquals("john@example.com", result.getEmail());
        verify(customerRepository, times(1)).findByEmail("john@example.com");
        verify(customerRepository, times(1)).save(any(Customer.class));
    }

    @Test
    void testCreateCustomer_DuplicateEmail() {
        when(customerRepository.findByEmail("john@example.com")).thenReturn(Optional.of(customer));

        assertThrows(ConflictException.class, () -> customerService.createCustomer(customerDTO));

        verify(customerRepository, times(1)).findByEmail("john@example.com");
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void testGetCustomer_Success() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerMapper.toDTO(any(Customer.class))).thenReturn(customerDTO);

        CustomerDTO result = customerService.getCustomer(1L);

        assertNotNull(result);
        assertEquals("john@example.com", result.getEmail());
        verify(customerRepository, times(1)).findById(1L);
    }

    @Test
    void testGetCustomer_NotFound() {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> customerService.getCustomer(999L));

        verify(customerRepository, times(1)).findById(999L);
    }

    @Test
    void testGetAllCustomers_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        List<Customer> customers = Arrays.asList(customer);
        Page<Customer> page = new PageImpl<>(customers, pageable, 1);

        when(customerRepository.findAll(pageable)).thenReturn(page);
        when(customerMapper.toDTO(any(Customer.class))).thenReturn(customerDTO);

        Page<CustomerDTO> result = customerService.getAllCustomers(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(customerRepository, times(1)).findAll(pageable);
    }

    @Test
    void testUpdateCustomer_Success() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenReturn(customer);
        when(customerMapper.toDTO(any(Customer.class))).thenReturn(customerDTO);

        CustomerDTO result = customerService.updateCustomer(1L, customerDTO);

        assertNotNull(result);
        assertEquals("john@example.com", result.getEmail());
        verify(customerRepository, times(1)).findById(1L);
        verify(customerRepository, times(1)).save(any(Customer.class));
    }

    @Test
    void testUpdateCustomer_NotFound() {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> customerService.updateCustomer(999L, customerDTO));

        verify(customerRepository, times(1)).findById(999L);
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void testUpdateCustomer_DuplicateEmail() {
        Customer anotherCustomer = Customer.builder()
                .id(2L)
                .email("jane@example.com")
                .firstName("Jane")
                .status(Customer.CustomerStatus.ACTIVE)
                .build();

        CustomerDTO updateDTO = CustomerDTO.builder()
                .email("jane@example.com")
                .firstName("Jane")
                .build();

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(anotherCustomer));

        assertThrows(ConflictException.class, () -> customerService.updateCustomer(1L, updateDTO));

        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void testDeleteCustomer_Success() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        doNothing().when(customerRepository).delete(any(Customer.class));

        customerService.deleteCustomer(1L);

        verify(customerRepository, times(1)).findById(1L);
        verify(customerRepository, times(1)).delete(any(Customer.class));
    }

    @Test
    void testDeleteCustomer_NotFound() {
        when(customerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> customerService.deleteCustomer(999L));

        verify(customerRepository, times(1)).findById(999L);
        verify(customerRepository, never()).delete(any(Customer.class));
    }
}

