package com.enterprise.order.order.controller;

import com.enterprise.order.order.dto.CreateOrderRequest;
import com.enterprise.order.order.dto.OrderDTO;
import com.enterprise.order.order.dto.UpdateOrderStatusRequest;
import com.enterprise.order.order.service.OrderService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order creation and lifecycle APIs")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Create a new order")
    public ResponseEntity<BaseResponse<OrderDTO>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("POST /api/v1/orders - Creating order");

        OrderDTO created = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(created, "Order created successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @orderSecurity.isOwner(#id, authentication)")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<BaseResponse<OrderDTO>> getOrder(@PathVariable("id") Long id) {
        log.info("GET /api/v1/orders/{} - Fetching order", id);

        OrderDTO order = orderService.getOrder(id);
        return ResponseEntity.ok(BaseResponse.success(order, "Order retrieved successfully"));
    }

    @GetMapping("/number/{orderNumber}")
    @PreAuthorize("hasRole('ADMIN') or @orderSecurity.isOwnerByNumber(#orderNumber, authentication)")
    @Operation(summary = "Get order by order number")
    public ResponseEntity<BaseResponse<OrderDTO>> getOrderByNumber(@PathVariable("orderNumber") String orderNumber) {
        log.info("GET /api/v1/orders/number/{} - Fetching order", orderNumber);

        OrderDTO order = orderService.getOrderByNumber(orderNumber);
        return ResponseEntity.ok(BaseResponse.success(order, "Order retrieved successfully"));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get orders with optional status filter")
    public ResponseEntity<BaseResponse<Page<OrderDTO>>> getOrders(
            @Parameter(description = "Filter by status") @RequestParam(name = "status", required = false) String status,
            Pageable pageable) {
        log.info("GET /api/v1/orders - Fetching orders");

        Page<OrderDTO> orders = orderService.getOrders(status, pageable);
        return ResponseEntity.ok(BaseResponse.success(orders, "Orders retrieved successfully"));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasRole('ADMIN') or #customerId.toString() == authentication.name")
    @Operation(summary = "Get orders for a customer")
    public ResponseEntity<BaseResponse<Page<OrderDTO>>> getOrdersByCustomer(@PathVariable("customerId") Long customerId,
                                                                             Pageable pageable) {
        log.info("GET /api/v1/orders/customer/{} - Fetching customer orders", customerId);

        Page<OrderDTO> orders = orderService.getOrdersByCustomer(customerId, pageable);
        return ResponseEntity.ok(BaseResponse.success(orders, "Customer orders retrieved successfully"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update order status")
    public ResponseEntity<BaseResponse<OrderDTO>> updateStatus(@PathVariable("id") Long id,
                                                                @Valid @RequestBody UpdateOrderStatusRequest request) {
        log.info("PATCH /api/v1/orders/{}/status - Updating order status", id);

        OrderDTO updated = orderService.updateStatus(id, request.getStatus());
        return ResponseEntity.ok(BaseResponse.success(updated, "Order status updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @orderSecurity.isOwner(#id, authentication)")
    @Operation(summary = "Cancel order")
    public ResponseEntity<BaseResponse<Void>> cancelOrder(@PathVariable("id") Long id) {
        log.info("DELETE /api/v1/orders/{} - Cancelling order", id);

        orderService.cancelOrder(id);
        return ResponseEntity.ok(BaseResponse.success(null, "Order cancelled successfully"));
    }
}
