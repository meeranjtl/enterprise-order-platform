package com.enterprise.order.order.mapper;

import com.enterprise.order.order.dto.OrderDTO;
import com.enterprise.order.order.dto.OrderItemDTO;
import com.enterprise.order.order.entity.Order;
import com.enterprise.order.order.entity.OrderItem;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    default OrderDTO toDTO(Order order) {
        if (order == null) {
            return null;
        }
        return OrderDTO.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerId(order.getCustomerId())
                .items(toItemDTOs(order.getItems()))
                .subtotal(order.getSubtotal())
                .tax(order.getTax())
                .shippingCost(order.getShippingCost())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus() == null ? null : order.getStatus().name())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    default List<OrderDTO> toDTOs(List<Order> orders) {
        return orders == null ? List.of() : orders.stream().map(this::toDTO).toList();
    }

    default OrderItemDTO toItemDTO(OrderItem item) {
        if (item == null) {
            return null;
        }
        return OrderItemDTO.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productSku(item.getProductSku())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .discount(item.getDiscount())
                .lineTotal(item.getLineTotal())
                .build();
    }

    default List<OrderItemDTO> toItemDTOs(List<OrderItem> items) {
        return items == null ? List.of() : items.stream().map(this::toItemDTO).toList();
    }
}
