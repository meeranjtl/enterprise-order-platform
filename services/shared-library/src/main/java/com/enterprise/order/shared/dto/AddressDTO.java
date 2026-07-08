package com.enterprise.order.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddressDTO {

    @NotBlank(message = "City is required")
    private String city;

    private String state;

    private String zipCode;

    @NotBlank(message = "Country is required")
    private String country;

    private String street;

    private String buildingNumber;
}

