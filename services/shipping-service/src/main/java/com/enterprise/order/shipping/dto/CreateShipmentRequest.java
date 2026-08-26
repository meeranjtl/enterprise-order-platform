package com.enterprise.order.shipping.dto;

import com.enterprise.order.shared.dto.AddressDTO;
import com.enterprise.order.shared.validation.ValidAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateShipmentRequest {

    @NotNull(message = "orderId is required")
    private Long orderId;

    private Long customerId;

    @Valid
    @ValidAddress
    private AddressDTO shippingAddress;
}
