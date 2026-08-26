package com.enterprise.order.order.client;

import com.enterprise.order.order.config.OrderClientProperties;
import com.enterprise.order.order.dto.CustomerLookupDTO;
import com.enterprise.order.shared.dto.BaseResponse;
import com.enterprise.order.shared.exception.BadRequestException;
import com.enterprise.order.shared.exception.InternalServerException;
import com.enterprise.order.shared.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Component
public class CustomerClient {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final RestClient restClient;

    public CustomerClient(RestClient.Builder restClientBuilder, OrderClientProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.getCustomerServiceUrl()).build();
    }

    public CustomerLookupDTO getCustomer(Long customerId) {
        try {
            BaseResponse<CustomerLookupDTO> response = restClient.get()
                    .uri("/api/v1/customers/{id}", customerId)
                    .headers(this::addCorrelationId)
                    .headers(this::addAuthorization)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new InternalServerException("Customer service returned an invalid response");
            }
            return response.getData();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Customer", String.valueOf(customerId));
            }
            if (ex.getStatusCode().is4xxClientError()) {
                throw new BadRequestException("Customer validation failed for ID: " + customerId);
            }
            log.warn("Customer service call failed for customerId={}: status={}", customerId, ex.getStatusCode());
            throw new InternalServerException("Customer service is unavailable");
        }
    }

    private void addCorrelationId(HttpHeaders headers) {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.isBlank()) {
            headers.set(CORRELATION_ID_HEADER, correlationId);
        }
    }

    // Phase 12 made every customer-service endpoint require a JWT (self-or-admin for
    // GET /{id}); this internal call went unauthenticated and started failing with a
    // wrapped 401->BadRequestException the first time anyone actually created an order
    // post-Phase-12 (Phase 12's own E2E validation never exercised order creation).
    // Forwarding the caller's own bearer token works because order creation is
    // CUSTOMER-only and customers can only ever look themselves up.
    private void addAuthorization(HttpHeaders headers) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String authHeader = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && !authHeader.isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, authHeader);
            }
        }
    }
}
