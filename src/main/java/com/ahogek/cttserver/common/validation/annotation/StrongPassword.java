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
 * Strong password composite validation annotation.
 *
 * <p>Combines {@code @NotBlank} and {@code @Pattern} for password strength validation.
 *
 * <p>Password requirements:
 *
 * <ul>
 *   <li>8-32 characters length
 *   <li>At least one uppercase letter
 *   <li>At least one lowercase letter
 *   <li>At least one digit
 *   <li>At least one special character (@$!%*?&)
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
@Pattern(
        regexp = ValidationConstants.REGEX_PASSWORD,
        message = ValidationConstants.MSG_PASSWORD_WEAK)
@ReportAsSingleViolation
public @interface StrongPassword {

    String message() default ValidationConstants.MSG_PASSWORD_WEAK;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
