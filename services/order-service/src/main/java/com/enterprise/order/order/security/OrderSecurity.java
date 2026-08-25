package com.enterprise.order.order.security;

import com.enterprise.order.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Backs the {@code @orderSecurity.isOwner(...)} {@code @PreAuthorize} expressions in
 * {@link com.enterprise.order.order.controller.OrderController}. {@code Order.customerId}
 * is a plain column with no relation, and most order endpoints don't take it as a path
 * variable, so ownership can't be expressed as pure SpEL — it has to load the order first.
 */
@Component("orderSecurity")
@RequiredArgsConstructor
public class OrderSecurity {

    private final OrderService orderService;

    public boolean isOwner(Long orderId, Authentication authentication) {
        return String.valueOf(orderService.getOrder(orderId).getCustomerId()).equals(authentication.getName());
    }

    public boolean isOwnerByNumber(String orderNumber, Authentication authentication) {
        return String.valueOf(orderService.getOrderByNumber(orderNumber).getCustomerId()).equals(authentication.getName());
    }
}
