package com.enterprise.order.shared.exception;

public class BadRequestException extends ApplicationException {
    public BadRequestException(String message) {
        super("BAD_REQUEST", message, 400);
    }
}

