package com.enterprise.order.gateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    /**
     * Short-timeout client used only for probing downstream {@code /actuator/health}
     * endpoints (System Health page, Phase 13) — never for proxying real API traffic,
     * which stays on Spring Cloud Gateway's own routing/CircuitBreaker path.
     */
    @Bean
    public WebClient healthWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                .responseTimeout(Duration.ofSeconds(3));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
