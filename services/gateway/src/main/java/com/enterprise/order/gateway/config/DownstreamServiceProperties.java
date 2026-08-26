package com.enterprise.order.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service-name -> base-URL map for the System Health page's health aggregator
 * (Phase 13). Deliberately separate from the {@code spring.cloud.gateway.routes}
 * list — those routes are Gateway's own predicate/filter DSL and aren't easily
 * readable back out as a plain map, so this duplicates the same URLs under a
 * config key built for that purpose. Keep both lists in sync when a service's
 * port changes (same caveat as everywhere else a URL is hardcoded per profile).
 */
@Component
@ConfigurationProperties(prefix = "app")
public class DownstreamServiceProperties {

    private Map<String, String> downstreamServices = new LinkedHashMap<>();

    public Map<String, String> getDownstreamServices() {
        return downstreamServices;
    }

    public void setDownstreamServices(Map<String, String> downstreamServices) {
        this.downstreamServices = downstreamServices;
    }
}
