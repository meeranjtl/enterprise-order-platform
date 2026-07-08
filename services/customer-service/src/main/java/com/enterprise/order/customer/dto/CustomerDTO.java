package com.enterprise.order.customer.dto;

import com.enterprise.order.shared.dto.AddressDTO;
import com.enterprise.order.shared.validation.ValidAddress;
import com.enterprise.order.shared.validation.ValidPhone;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerDTO {

    private Long id;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @ValidPhone(message = "Phone number must be valid")
    private String phone;

    @Valid
    @ValidAddress
    private AddressDTO address;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

