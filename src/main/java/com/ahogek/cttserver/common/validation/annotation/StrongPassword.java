package com.ahogek.cttserver.common.validation.annotation;

import com.ahogek.cttserver.common.validation.ValidationConstants;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Password validation annotation enforcing length-only constraint (NIST SP 800-63B).
 *
 * <p>Combines {@code @NotBlank} and {@code @Size} for password validation.
 *
 * <p>Password requirements:
 *
 * <ul>
 *   <li>8-64 characters length
 *   <li>No complexity requirements (uppercase, digit, special char are optional)
 * </ul>
 *
 * @author AhogeK [ahogek@gmail.com]
 * @since 2026-03-16
 */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@NotBlank(message = ValidationConstants.MSG_NOT_BLANK)
@Size(
        min = ValidationConstants.PASSWORD_MIN_LENGTH,
        max = ValidationConstants.PASSWORD_MAX_LENGTH,
        message = ValidationConstants.MSG_PASSWORD_WEAK)
@ReportAsSingleViolation
public @interface StrongPassword {

    String message() default ValidationConstants.MSG_PASSWORD_WEAK;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
