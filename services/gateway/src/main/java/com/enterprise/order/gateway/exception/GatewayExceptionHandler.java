package com.enterprise.order.gateway.exception;

import com.enterprise.order.gateway.dto.GatewayErrorResponse;
import com.enterprise.order.gateway.filter.CorrelationIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Reactive error handler for errors raised at the gateway (as opposed to errors returned by
 * downstream services, which already carry the {@code BaseResponse} shape and pass through
 * untouched). Runs before Spring Boot's {@code DefaultErrorWebExceptionHandler} (order -1).
 * <p>
 * Error code mapping:
 * <ul>
 *     <li>No matching route (404) → {@code ROUTE_NOT_FOUND}</li>
 *     <li>Circuit breaker open → {@code CIRCUIT_BREAKER_OPEN} (503)</li>
 *     <li>Other {@link ResponseStatusException} → its status, code = status name</li>
 *     <li>Anything else → {@code GATEWAY_ERROR} (500)</li>
 * </ul>
 * Note: unknown-route 404s are raised during route matching, before any {@code GlobalFilter}
 * runs — so this handler also echoes/generates the correlation ID header itself.
 */
@Slf4j
@Component
@Order(-2)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GatewayExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status;
        String code;
        String message;

        if (ex instanceof ResponseStatusException rse) {
            HttpStatus resolved = HttpStatus.resolve(rse.getStatusCode().value());
            status = resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
            if (status == HttpStatus.NOT_FOUND) {
                code = "ROUTE_NOT_FOUND";
                message = "No route matched the requested path";
            } else {
                code = status.name();
                message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
            }
        } else if (ex instanceof CallNotPermittedException) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            code = "CIRCUIT_BREAKER_OPEN";
            message = "Service is temporarily unavailable (circuit breaker open)";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = "GATEWAY_ERROR";
            message = "An unexpected error occurred at the gateway";
        }

        String correlationId = exchange.getRequest().getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        response.getHeaders().set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);

        log.error("Gateway error [corr={}] {} {} -> {} {}: {}",
                correlationId,
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath(),
                status.value(),
                code,
                ex.getMessage());

        GatewayErrorResponse body = GatewayErrorResponse.of(code, message, null);

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = "{\"success\":false}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
