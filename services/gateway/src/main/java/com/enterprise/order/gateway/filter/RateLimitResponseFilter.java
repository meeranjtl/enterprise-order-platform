package com.enterprise.order.gateway.filter;

import com.enterprise.order.gateway.dto.GatewayErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Gives rate-limited (429) responses a consistent {@code BaseResponse}-shaped JSON body.
 * <p>
 * The built-in {@code RequestRateLimiter} gateway filter rejects by setting status 429 and
 * calling {@code setComplete()} — which commits an <em>empty</em> body. This filter runs before
 * the route filters and decorates the response so that a {@code setComplete()} with status 429
 * instead writes the standard error envelope plus a {@code Retry-After} header.
 */
@Component
public class RateLimitResponseFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper;

    public RateLimitResponseFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> setComplete() {
                HttpStatusCode status = getStatusCode();
                if (status != null && status.value() == HttpStatus.TOO_MANY_REQUESTS.value() && !isCommitted()) {
                    getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    getHeaders().set("Retry-After", "1");
                    byte[] bytes = serialize(GatewayErrorResponse.of(
                            "RATE_LIMIT_EXCEEDED",
                            "Too many requests",
                            "Rate limit exceeded. Please retry shortly."));
                    DataBuffer buffer = bufferFactory().wrap(bytes);
                    return writeWith(Mono.just(buffer));
                }
                return super.setComplete();
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    private byte[] serialize(GatewayErrorResponse body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            return "{\"success\":false}".getBytes(StandardCharsets.UTF_8);
        }
    }
}
