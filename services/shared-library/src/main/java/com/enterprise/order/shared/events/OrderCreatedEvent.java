package com.enterprise.order.shared.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreatedEvent {

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("orderNumber")
    private String orderNumber;

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("totalAmount")
    private Double totalAmount;

    @JsonProperty("orderItems")
    private List<OrderItem> orderItems;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    public static final String TOPIC = "order-events";
    public static final String EVENT_TYPE = "OrderCreated";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItem {
        @JsonProperty("productId")
        private String productId;

        @JsonProperty("quantity")
        private Integer quantity;

        @JsonProperty("unitPrice")
        private Double unitPrice;
    }
}
