package com.enterprise.order.gateway.dto;

public record ServiceHealthStatus(String name, String status, String detail) {

    public static ServiceHealthStatus up(String name) {
        return new ServiceHealthStatus(name, "UP", null);
    }

    public static ServiceHealthStatus down(String name, String detail) {
        return new ServiceHealthStatus(name, "DOWN", detail);
    }
}
