package com.enterprise.order.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end gateway tests: real Netty server, real Redis (Testcontainers),
 * downstream services stubbed with WireMock on dynamic ports.
 * <p>
 * Skipped automatically in environments without Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
// Fixed ordering: the rate-limit test deliberately drains the shared Redis token bucket
// (keyed by client IP), so it must run last or the routing tests intermittently see 429s.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewayRoutingIT {

    // WireMock must be running before @DynamicPropertySource resolves the port placeholders,
    // so it is started in a static initializer (a @BeforeAll would be too late).
    static final WireMockServer customerStub = new WireMockServer(wireMockConfig().dynamicPort());
    static final WireMockServer productStub = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        customerStub.start();
        productStub.start();
    }

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("wiremock.customer.port", customerStub::port);
        registry.add("wiremock.product.port", productStub::port);
    }

    @LocalServerPort
    private int port;

    private WebClient client;

    @BeforeEach
    void setUp() {
        customerStub.resetAll();
        productStub.resetAll();
        customerStub.stubFor(get(urlEqualTo("/api/v1/customers/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Ada Lovelace\"}")));
        customerStub.stubFor(get(urlEqualTo("/api/v1/customers"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));
        productStub.stubFor(get(urlEqualTo("/api/v1/products/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Widget\"}")));

        client = WebClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @AfterAll
    static void tearDown() {
        customerStub.stop();
        productStub.stop();
    }

    @Test
    @Order(1)
    void routesRequestToCustomerService() {
        String body = client.get().uri("/api/v1/customers/1")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));

        assertThat(body).contains("Ada Lovelace");
        customerStub.verify(getRequestedFor(urlEqualTo("/api/v1/customers/1")));
    }

    @Test
    @Order(2)
    void routesRequestToProductService() {
        String body = client.get().uri("/api/v1/products/1")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));

        assertThat(body).contains("Widget");
        productStub.verify(getRequestedFor(urlEqualTo("/api/v1/products/1")));
    }

    @Test
    @Order(3)
    void unknownRouteReturns404WithRouteNotFoundEnvelope() {
        var response = client.get().uri("/api/v1/unknown")
                .exchangeToMono(r -> r.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(b -> new StatusAndBody(r.statusCode(), b)))
                .block(Duration.ofSeconds(10));

        assertThat(response).isNotNull();
        assertThat(response.status().value()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"ROUTE_NOT_FOUND\"").contains("\"success\":false");
    }

    @Test
    @Order(4)
    void propagatesCorrelationIdToDownstreamService() {
        client.get().uri("/api/v1/customers/1")
                .header("X-Correlation-Id", "it-corr-123")
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));

        customerStub.verify(getRequestedFor(urlEqualTo("/api/v1/customers/1"))
                .withHeader("X-Correlation-Id", equalTo("it-corr-123")));
    }

    @Test
    @Order(5)
    void echoesProvidedCorrelationIdInResponse() {
        String echoed = client.get().uri("/api/v1/customers/1")
                .header("X-Correlation-Id", "echo-me-456")
                .exchangeToMono(r -> Mono.justOrEmpty(r.headers().asHttpHeaders().getFirst("X-Correlation-Id")))
                .block(Duration.ofSeconds(10));

        assertThat(echoed).isEqualTo("echo-me-456");
    }

    @Test
    @Order(6)
    void generatesCorrelationIdWhenNoneProvided() {
        String generated = client.get().uri("/api/v1/customers/1")
                .exchangeToMono(r -> Mono.justOrEmpty(r.headers().asHttpHeaders().getFirst("X-Correlation-Id")))
                .block(Duration.ofSeconds(10));

        assertThat(generated).isNotBlank();
    }

    @Test
    @Order(7) // must stay last: drains the shared Redis token bucket
    void rateLimitExceededReturns429WithJsonEnvelope() {
        // default-filters allow a burst of 20 (replenish 10/s). 40 rapid calls from the
        // same client must trip the limiter at least once.
        List<Integer> statuses = new ArrayList<>();
        String last429Body = null;
        HttpHeaders last429Headers = null;

        for (int i = 0; i < 40; i++) {
            var response = client.get().uri("/api/v1/customers")
                    .exchangeToMono(r -> r.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(b -> new RawResponse(r.statusCode(), b, r.headers().asHttpHeaders())))
                    .block(Duration.ofSeconds(10));
            assertThat(response).isNotNull();
            statuses.add(response.status().value());
            if (response.status().value() == 429) {
                last429Body = response.body();
                last429Headers = response.headers();
            }
        }

        assertThat(statuses).contains(429);
        assertThat(statuses).contains(200);
        assertThat(last429Body)
                .contains("\"code\":\"RATE_LIMIT_EXCEEDED\"")
                .contains("\"success\":false");
        assertThat(last429Headers)
                .as("headers of a 429 response")
                .isNotNull();
        if (last429Headers != null) { // satisfies the null analyzer after the assertion above
            assertThat(last429Headers.getFirst("Retry-After")).isEqualTo("1");
            assertThat(last429Headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        }
    }

    record StatusAndBody(HttpStatusCode status, String body) {
    }

    record RawResponse(HttpStatusCode status, String body, HttpHeaders headers) {
    }
}
