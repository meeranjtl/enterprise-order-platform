package com.enterprise.order.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PhoneValidator implements ConstraintValidator<ValidPhone, String> {

    private static final String PHONE_PATTERN = "^[+]?[0-9]{10,15}(?:[-\\s]?[0-9]{1,4})*$";

    @Override
    public void initialize(ValidPhone constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // Allow null/blank - use @NotNull/@NotBlank for mandatory fields
        }
        return value.matches(PHONE_PATTERN);
    }
}

