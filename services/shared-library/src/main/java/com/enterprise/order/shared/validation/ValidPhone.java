package com.enterprise.order.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneValidator.class)
@Documented
public @interface ValidPhone {
    String message() default "Phone number must be valid (10-15 digits, optionally with + and hyphens)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

