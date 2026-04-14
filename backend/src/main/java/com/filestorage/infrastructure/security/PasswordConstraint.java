package com.filestorage.infrastructure.security;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom password constraint: 8–16 chars, at least 1 uppercase, 1 lowercase,
 * 1 digit, and 1 special character.
 */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE,
         ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Pattern(regexp = "^(?=.*\\d)(?=.*[A-Z])(?=.*[a-z])(?=.*[^\\w\\s:])(\\S){8,16}$")
@ReportAsSingleViolation
public @interface PasswordConstraint {

    String message() default Messages.Validation.PASSWORD;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

