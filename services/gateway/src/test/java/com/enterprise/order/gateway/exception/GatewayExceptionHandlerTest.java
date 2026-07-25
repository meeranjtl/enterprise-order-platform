package com.enterprise.order.gateway.exception;

import com.enterprise.order.gateway.filter.CorrelationIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayExceptionHandlerTest {

    private GatewayExceptionHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Mirrors Spring Boot's auto-configured ObjectMapper (JavaTimeModule, ISO dates)
        objectMapper = org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
        handler = new GatewayExceptionHandler(objectMapper);
    }

    @Test
    void routeNotFoundReturns404WithRouteNotFoundCode() throws Exception {
        MockServerWebExchange exchange = exchangeFor("/api/v1/unknown");

        handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode body = objectMapper.readTree(bodyOf(exchange));
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("error").get("code").asText()).isEqualTo("ROUTE_NOT_FOUND");
        // Timestamp is present (serialization format follows the runtime mapper, same as the services)
        assertThat(body.hasNonNull("timestamp")).isTrue();
    }

    @Test
    void circuitBreakerOpenReturns503() throws Exception {
        MockServerWebExchange exchange = exchangeFor("/api/v1/customers/1");
        CallNotPermittedException ex = CallNotPermittedException
                .createCallNotPermittedException(CircuitBreaker.ofDefaults("testCB"));

        handler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        JsonNode body = objectMapper.readTree(bodyOf(exchange));
        assertThat(body.get("error").get("code").asText()).isEqualTo("CIRCUIT_BREAKER_OPEN");
    }

    @Test
    void genericExceptionReturns500GatewayError() throws Exception {
        MockServerWebExchange exchange = exchangeFor("/api/v1/customers/1");

        handler.handle(exchange, new RuntimeException("boom")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        JsonNode body = objectMapper.readTree(bodyOf(exchange));
        assertThat(body.get("error").get("code").asText()).isEqualTo("GATEWAY_ERROR");
    }

    @Test
    void otherResponseStatusExceptionUsesItsStatusAndName() throws Exception {
        MockServerWebExchange exchange = exchangeFor("/api/v1/customers");

        handler.handle(exchange, new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad input")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = objectMapper.readTree(bodyOf(exchange));
        assertThat(body.get("error").get("code").asText()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void echoesExistingCorrelationIdHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/unknown")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "err-corr-42"));

        handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND)).block();

        assertThat(exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo("err-corr-42");
    }

    @Test
    void generatesCorrelationIdHeaderWhenAbsent() {
        MockServerWebExchange exchange = exchangeFor("/api/v1/unknown");

        handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND)).block();

        String header = exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(header).isNotBlank();
        UUID.fromString(header);
    }

    @Test
    void committedResponsePropagatesErrorInsteadOfWriting() {
        MockServerWebExchange exchange = exchangeFor("/api/v1/customers");
        exchange.getResponse().setComplete().block(); // commits the response
        RuntimeException ex = new RuntimeException("too late");

        StepVerifier.create(handler.handle(exchange, ex))
                .verifyErrorSatisfies(t -> assertThat(t).isSameAs(ex));
    }

    private MockServerWebExchange exchangeFor(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path));
    }

    private String bodyOf(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }
}
