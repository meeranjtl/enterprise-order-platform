package com.enterprise.order.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AddressValidator.class)
@Documented
public @interface ValidAddress {
    String message() default "Address must contain at least a city";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

