package com.enterprise.order.order.client;

import com.enterprise.order.order.config.OrderClientProperties;
import com.enterprise.order.order.dto.ProductLookupDTO;
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
public class ProductClient {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final RestClient restClient;

    public ProductClient(RestClient.Builder restClientBuilder, OrderClientProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.getProductServiceUrl()).build();
    }

    public ProductLookupDTO getProduct(Long productId) {
        try {
            BaseResponse<ProductLookupDTO> response = restClient.get()
                    .uri("/api/v1/products/{id}", productId)
                    .headers(this::addCorrelationId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || !response.isSuccess() || response.getData() == null) {
                throw new InternalServerException("Product service returned an invalid response");
            }
            return response.getData();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Product", String.valueOf(productId));
            }
            if (ex.getStatusCode().is4xxClientError()) {
                throw new BadRequestException("Product validation failed for ID: " + productId);
            }
            log.warn("Product service call failed for productId={}: status={}", productId, ex.getStatusCode());
            throw new InternalServerException("Product service is unavailable");
        }
    }

    private void addCorrelationId(HttpHeaders headers) {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.isBlank()) {
            headers.set(CORRELATION_ID_HEADER, correlationId);
        }
    }
}
