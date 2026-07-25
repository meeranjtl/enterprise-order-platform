package com.enterprise.order.shared.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Propagates the gateway-supplied correlation ID into each servlet-based service:
 * places it in the SLF4J MDC (key {@code correlationId}, picked up by the services'
 * {@code logging.pattern.level}) so every service log line is traceable back to the
 * originating gateway request.
 * <p>
 * Deliberately does NOT echo the header on the response — the gateway owns the client-facing
 * response header. If a service echoed it too, the gateway would merge the downstream copy
 * back in and clients would see the header twice.
 * <p>
 * Added in Phase 4 so requests can be traced across gateway → service logs.
 * Only active when the {@value #CORRELATION_ID_HEADER} header is present (i.e. the request
 * came through the gateway); direct calls to a service log unchanged. Auto-registered in
 * customer/product services via their existing {@code @ComponentScan("com.enterprise.order.shared")}.
 * The {@code SERVLET} condition keeps it inert if this library ever lands on a reactive classpath.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CorrelationIdLoggingFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }
}
