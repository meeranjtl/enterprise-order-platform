package com.enterprise.order.shared.validation;

import com.enterprise.order.shared.dto.AddressDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AddressValidator implements ConstraintValidator<ValidAddress, AddressDTO> {

    @Override
    public void initialize(ValidAddress constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(AddressDTO value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Allow null - use @NotNull for mandatory fields
        }

        // At minimum, city should be present
        return value.getCity() != null && !value.getCity().isBlank();
    }
}

