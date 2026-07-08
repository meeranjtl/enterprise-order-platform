package com.enterprise.order.customer.service;

import com.enterprise.order.customer.dto.CustomerDTO;
import com.enterprise.order.customer.entity.Customer;
import com.enterprise.order.customer.mapper.CustomerMapper;
import com.enterprise.order.customer.repository.CustomerRepository;
import com.enterprise.order.customer.specification.CustomerSpecification;
import com.enterprise.order.shared.exception.ConflictException;
import com.enterprise.order.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    public CustomerDTO createCustomer(CustomerDTO customerDTO) {
        log.info("Creating customer with email: {}", customerDTO.getEmail());

        // Check if email already exists
        if (customerRepository.findByEmail(customerDTO.getEmail()).isPresent()) {
            throw new ConflictException("Customer with email " + customerDTO.getEmail() + " already exists");
        }

        Customer customer = customerMapper.toEntity(customerDTO);
        customer.setStatus(Customer.CustomerStatus.ACTIVE);

        Customer savedCustomer = customerRepository.save(customer);
        log.info("Customer created with id: {}", savedCustomer.getId());

        return customerMapper.toDTO(savedCustomer);
    }

    @Transactional(readOnly = true)
    public CustomerDTO getCustomer(Long id) {
        log.info("Fetching customer with id: {}", id);

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id.toString()));

        return customerMapper.toDTO(customer);
    }

    @Transactional(readOnly = true)
    public Page<CustomerDTO> getAllCustomers(Pageable pageable) {
        log.info("Fetching all customers, page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());

        return customerRepository.findAll(pageable)
                .map(customerMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<CustomerDTO> searchCustomers(
            String email,
            String firstName,
            String lastName,
            String status,
            String city,
            String country,
            Pageable pageable) {
        log.info("Searching customers with email: {}, firstName: {}, lastName: {}, status: {}, city: {}, country: {}",
                email, firstName, lastName, status, city, country);

        Specification<Customer> spec = Specification
                .where(CustomerSpecification.emailContains(email))
                .and(CustomerSpecification.firstNameContains(firstName))
                .and(CustomerSpecification.lastNameContains(lastName))
                .and(CustomerSpecification.statusEquals(status))
                .and(CustomerSpecification.cityContains(city))
                .and(CustomerSpecification.countryContains(country));

        return customerRepository.findAll(spec, pageable)
                .map(customerMapper::toDTO);
    }

    public CustomerDTO updateCustomer(Long id, CustomerDTO customerDTO) {
        log.info("Updating customer with id: {}", id);

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id.toString()));

        // Check if email is being changed and if new email already exists
        if (!customer.getEmail().equals(customerDTO.getEmail()) &&
            customerRepository.findByEmail(customerDTO.getEmail()).isPresent()) {
            throw new ConflictException("Customer with email " + customerDTO.getEmail() + " already exists");
        }

        customer.setEmail(customerDTO.getEmail());
        customer.setFirstName(customerDTO.getFirstName());
        customer.setLastName(customerDTO.getLastName());
        customer.setPhone(customerDTO.getPhone());

        // Update address fields from AddressDTO
        if (customerDTO.getAddress() != null) {
            customer.setCity(customerDTO.getAddress().getCity());
            customer.setState(customerDTO.getAddress().getState());
            customer.setZipCode(customerDTO.getAddress().getZipCode());
            customer.setCountry(customerDTO.getAddress().getCountry());
        }

        Customer updatedCustomer = customerRepository.save(customer);
        log.info("Customer updated with id: {}", updatedCustomer.getId());

        return customerMapper.toDTO(updatedCustomer);
    }

    public void deleteCustomer(Long id) {
        log.info("Deleting customer with id: {}", id);

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id.toString()));

        customerRepository.delete(customer);
        log.info("Customer deleted with id: {}", id);
    }
}

