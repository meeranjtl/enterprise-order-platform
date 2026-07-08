package com.enterprise.order.shared.exception;

public class InternalServerException extends ApplicationException {
    public InternalServerException(String message) {
        super("INTERNAL_SERVER_ERROR", message, 500);
    }
}

