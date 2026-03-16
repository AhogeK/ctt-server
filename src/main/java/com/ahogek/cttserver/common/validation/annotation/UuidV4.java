package com.ahogek.cttserver.common.validation.annotation;

import com.ahogek.cttserver.common.validation.ValidationConstants;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * UUID v4 format validation annotation.
 *
 * <p>Combines {@code @NotBlank} and {@code @Pattern} for strict UUID v4 validation.
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@NotBlank(message = ValidationConstants.MSG_NOT_BLANK)
@Pattern(regexp = ValidationConstants.REGEX_UUID_V4, message = ValidationConstants.MSG_UUID_INVALID)
@ReportAsSingleViolation
public @interface UuidV4 {

    String message() default ValidationConstants.MSG_UUID_INVALID;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
