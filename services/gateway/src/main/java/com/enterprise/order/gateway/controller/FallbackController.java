package com.enterprise.order.gateway.controller;

import com.enterprise.order.gateway.dto.GatewayErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Circuit-breaker fallback endpoints. Each route's {@code CircuitBreaker} filter forwards here
 * ({@code fallbackUri: forward:/fallback/<service>}) when the downstream service fails or times
 * out, so callers receive a consistent 503 envelope instead of a raw connection error.
 * <p>
 * Mapped for all mutating methods too — a POST to a downed service must fall back as well.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping(value = "/customer-service", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<GatewayErrorResponse> customerServiceFallback() {
        return buildFallback("Customer service");
    }

    @RequestMapping(value = "/product-service", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<GatewayErrorResponse> productServiceFallback() {
        return buildFallback("Product service");
    }

    @RequestMapping(value = "/order-service", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<GatewayErrorResponse> orderServiceFallback() {
        return buildFallback("Order service");
    }

    private ResponseEntity<GatewayErrorResponse> buildFallback(String service) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(GatewayErrorResponse.of(
                        "SERVICE_UNAVAILABLE",
                        service + " is currently unavailable",
                        "The downstream service did not respond. Please try again later."));
    }
}
