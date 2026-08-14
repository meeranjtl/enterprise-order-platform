package com.enterprise.order.shipping.dto;

import com.enterprise.order.shared.dto.AddressDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentDTO {

    private Long id;
    private Long orderId;
    private Long customerId;
    private String trackingNumber;
    private String status;
    private AddressDTO shippingAddress;
    private String packingList;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
