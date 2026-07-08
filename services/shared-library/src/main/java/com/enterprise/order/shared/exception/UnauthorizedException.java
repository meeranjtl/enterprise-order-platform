package com.enterprise.order.shared.exception;

public class UnauthorizedException extends ApplicationException {
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message, 401);
    }
}

