package com.enterprise.order.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Hooks;

/**
 * API Gateway entry point (Phase 4). Routes are defined declaratively in application.yml
 * (and application-docker.yml for container hostnames) — no programmatic RouteLocator needed.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        // Propagates reactor context (e.g. the correlation ID) into ThreadLocals such as SLF4J MDC
        // across thread hops. Requires io.micrometer:context-propagation on the classpath.
        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(GatewayApplication.class, args);
    }
}
