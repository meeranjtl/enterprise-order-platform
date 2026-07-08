package com.enterprise.order.shared.exception;

public class ConflictException extends ApplicationException {
    public ConflictException(String message) {
        super("CONFLICT", message, 409);
    }
}

