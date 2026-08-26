package com.enterprise.order.gateway.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SystemHealthResponse(String gatewayStatus, List<ServiceHealthStatus> services, LocalDateTime checkedAt) {
}
