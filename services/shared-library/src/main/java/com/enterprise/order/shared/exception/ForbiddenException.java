package com.enterprise.order.shared.exception;

public class ForbiddenException extends ApplicationException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message, 403);
    }
}

