package com.enterprise.order.customer.controller;

import com.enterprise.order.customer.dto.CustomerDTO;
import com.enterprise.order.customer.service.CustomerService;
import com.enterprise.order.shared.dto.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer management APIs")
public class CustomerController {

    private final CustomerService customerService;

    // Public self-registration goes through /api/auth/register; direct creation here
    // (e.g. an admin back-office tool creating a customer without a password) is admin-only.
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new customer")
    public ResponseEntity<BaseResponse<CustomerDTO>> createCustomer(@Valid @RequestBody CustomerDTO customerDTO) {
        log.info("POST /api/v1/customers - Creating customer");

        CustomerDTO created = customerService.createCustomer(customerDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(created, "Customer created successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.name")
    @Operation(summary = "Get customer by ID")
    public ResponseEntity<BaseResponse<CustomerDTO>> getCustomer(@PathVariable("id") Long id) {
        log.info("GET /api/v1/customers/{} - Fetching customer", id);

        CustomerDTO customer = customerService.getCustomer(id);
        return ResponseEntity.ok(BaseResponse.success(customer, "Customer retrieved successfully"));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all customers with pagination")
    public ResponseEntity<BaseResponse<Page<CustomerDTO>>> getAllCustomers(Pageable pageable) {
        log.info("GET /api/v1/customers - Fetching all customers");

        Page<CustomerDTO> customers = customerService.getAllCustomers(pageable);
        return ResponseEntity.ok(BaseResponse.success(customers, "Customers retrieved successfully"));
    }

    @GetMapping("/search/advanced")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Search customers with advanced filtering")
    public ResponseEntity<BaseResponse<Page<CustomerDTO>>> searchCustomers(
            @Parameter(description = "Filter by email") @RequestParam(name = "email", required = false) String email,
            @Parameter(description = "Filter by first name") @RequestParam(name = "firstName", required = false) String firstName,
            @Parameter(description = "Filter by last name") @RequestParam(name = "lastName", required = false) String lastName,
            @Parameter(description = "Filter by status (ACTIVE, INACTIVE, SUSPENDED, DELETED)") @RequestParam(name = "status", required = false) String status,
            @Parameter(description = "Filter by city") @RequestParam(name = "city", required = false) String city,
            @Parameter(description = "Filter by country") @RequestParam(name = "country", required = false) String country,
            Pageable pageable) {
        log.info("GET /api/v1/customers/search/advanced - Searching customers");

        Page<CustomerDTO> customers = customerService.searchCustomers(email, firstName, lastName, status, city, country, pageable);
        return ResponseEntity.ok(BaseResponse.success(customers, "Customers found successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.name")
    @Operation(summary = "Update customer")
    public ResponseEntity<BaseResponse<CustomerDTO>> updateCustomer(@PathVariable("id") Long id,
                                                                     @Valid @RequestBody CustomerDTO customerDTO) {
        log.info("PUT /api/v1/customers/{} - Updating customer", id);

        CustomerDTO updated = customerService.updateCustomer(id, customerDTO);
        return ResponseEntity.ok(BaseResponse.success(updated, "Customer updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete customer")
    public ResponseEntity<BaseResponse<Void>> deleteCustomer(@PathVariable("id") Long id) {
        log.info("DELETE /api/v1/customers/{} - Deleting customer", id);

        customerService.deleteCustomer(id);
        return ResponseEntity.ok(BaseResponse.success(null, "Customer deleted successfully"));
    }
}

