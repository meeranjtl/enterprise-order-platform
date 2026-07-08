package com.enterprise.order.shared.exception;

public class ResourceNotFoundException extends ApplicationException {
    public ResourceNotFoundException(String resource, String identifier) {
        super("RESOURCE_NOT_FOUND",
              resource + " not found with identifier: " + identifier,
              404);
    }
}

