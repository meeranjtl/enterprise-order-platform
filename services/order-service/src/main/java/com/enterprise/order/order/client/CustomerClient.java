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
}
