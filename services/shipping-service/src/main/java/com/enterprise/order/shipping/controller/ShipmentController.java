package com.enterprise.order.shipping.controller;

import com.enterprise.order.shared.dto.BaseResponse;
import com.enterprise.order.shipping.dto.CreateShipmentRequest;
import com.enterprise.order.shipping.dto.ShipmentDTO;
import com.enterprise.order.shipping.service.ShippingService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShippingService shippingService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a shipment for an order (idempotent per order)")
    public ResponseEntity<BaseResponse<ShipmentDTO>> create(
            @Valid @RequestBody CreateShipmentRequest request) {
        ShipmentDTO shipment = shippingService.createShipment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(shipment, "Shipment created"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a shipment by id")
    public BaseResponse<ShipmentDTO> get(@PathVariable Long id) {
        return BaseResponse.success(shippingService.get(id), "Shipment retrieved successfully");
    }

    @GetMapping(params = "orderId")
    @Operation(summary = "Get the shipment for an order")
    public BaseResponse<ShipmentDTO> getByOrderId(@RequestParam Long orderId) {
        return BaseResponse.success(shippingService.getByOrderId(orderId), "Shipment retrieved successfully");
    }

    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mark a shipped shipment as delivered (simulation)")
    public BaseResponse<ShipmentDTO> deliver(@PathVariable Long id) {
        return BaseResponse.success(shippingService.deliver(id), "Shipment delivered");
    }
}
