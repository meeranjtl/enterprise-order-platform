package com.enterprise.order.order.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Data
@ConfigurationProperties(prefix = "order.pricing")
public class OrderPricingProperties {
    private BigDecimal taxRate = new BigDecimal("0.10");
    private BigDecimal flatShippingCost = new BigDecimal("10.00");
    private BigDecimal freeShippingThreshold = new BigDecimal("100.00");
}
