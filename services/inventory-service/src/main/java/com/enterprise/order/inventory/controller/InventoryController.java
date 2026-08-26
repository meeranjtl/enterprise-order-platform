package com.enterprise.order.inventory.controller;

import com.enterprise.order.inventory.dto.InventoryDTO;
import com.enterprise.order.inventory.dto.ReservationRequest;
import com.enterprise.order.inventory.dto.StockAdjustmentRequest;
import com.enterprise.order.inventory.service.InventoryService;
import com.enterprise.order.shared.dto.BaseResponse;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    @Operation(summary = "Get inventory status")
    public BaseResponse<InventoryDTO> getInventory(@PathVariable Long productId) {
        return BaseResponse.success(inventoryService.get(productId), "Inventory retrieved successfully");
    }

    @PostMapping("/reserve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reserve inventory")
    public ResponseEntity<BaseResponse<InventoryDTO>> reserve(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReservationRequest request) {
        InventoryDTO inventory = inventoryService.reserve(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(inventory, "Inventory reserved successfully"));
    }

    @PostMapping("/release")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Release inventory")
    public BaseResponse<InventoryDTO> release(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReservationRequest request) {
        return BaseResponse.success(
                inventoryService.release(request, idempotencyKey),
                "Inventory released successfully");
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Adjust stock")
    public BaseResponse<InventoryDTO> adjust(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StockAdjustmentRequest request) {
        return BaseResponse.success(
                inventoryService.adjust(request, idempotencyKey),
                "Inventory adjusted successfully");
    }
}
