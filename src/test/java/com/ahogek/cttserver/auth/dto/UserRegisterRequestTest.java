package com.ahogek.cttserver.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class UserRegisterRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    void valid_registration_request_passes_validation() {
        UserRegisterRequest request =
                new UserRegisterRequest("user@example.com", "john_doe", "Password123!");

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void blank_email_is_invalid() {
        UserRegisterRequest request = new UserRegisterRequest("", "john", "Password123!");

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("Field cannot be blank");
    }

    @Test
    void null_email_is_invalid() {
        UserRegisterRequest request = new UserRegisterRequest(null, "john", "Password123!");

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("Field cannot be blank");
    }

    @Test
    void invalid_email_format_is_rejected() {
        UserRegisterRequest request =
                new UserRegisterRequest("not-an-email", "john", "Password123!");

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("Invalid email format");
    }

    @Test
    void blank_display_name_is_invalid() {
        UserRegisterRequest request = new UserRegisterRequest("user@test.com", "", "Password123!");

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("Field cannot be blank");
    }

    @Test
    void display_name_with_special_chars_is_invalid() {
        UserRegisterRequest request =
                new UserRegisterRequest("user@test.com", "john@doe", "Password123!");

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains(
                        "Display name must be 2-50 characters and contain only letters, numbers, underscores, or hyphens");
    }

    @Test
    void display_name_too_short_is_invalid() {
        UserRegisterRequest request = new UserRegisterRequest("user@test.com", "a", "Password123!");

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains(
                        "Display name must be 2-50 characters and contain only letters, numbers, underscores, or hyphens");
    }

    @Test
    void valid_display_names_accepted() {
        assertValidDisplayName("john_doe");
        assertValidDisplayName("John-Doe");
        assertValidDisplayName("User123");
        assertValidDisplayName("张三");
    }

    private void assertValidDisplayName(String displayName) {
        UserRegisterRequest request =
                new UserRegisterRequest("user@test.com", displayName, "Password123!");
        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        Set<String> displayNameErrors =
                violations.stream()
                        .filter(v -> v.getPropertyPath().toString().equals("displayName"))
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.toSet());

        assertThat(displayNameErrors).isEmpty();
    }

    @Test
    void blank_password_is_invalid() {
        UserRegisterRequest request = new UserRegisterRequest("user@test.com", "john", "");

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains(
                        "Password must be 8-32 characters long, including uppercase, lowercase, number, and special character");
    }

    @Test
    void weak_password_is_rejected() {
        UserRegisterRequest request = new UserRegisterRequest("user@test.com", "john", "weak");

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains(
                        "Password must be 8-32 characters long, including uppercase, lowercase, number, and special character");
    }

    @ParameterizedTest
    @CsvSource({
        "password123!, missing uppercase",
        "PASSWORD123!, missing lowercase",
        "Password!!!, missing digit",
        "Password1234, missing special character"
    })
    void invalid_passwords_are_rejected(String password, String description) {
        UserRegisterRequest request =
                new UserRegisterRequest("user@test.com", "john", password);

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .withFailMessage("Expected rejection for password with " + description)
                .hasSize(1);
    }

    @Test
    void strong_passwords_accepted() {
        assertValidPassword("Password123!");
        assertValidPassword("MyP@ssw0rd");
        assertValidPassword("Test@1234");
        assertValidPassword("A1b2c3d4$");
    }

    private void assertValidPassword(String password) {
        UserRegisterRequest request = new UserRegisterRequest("user@test.com", "john", password);
        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        Set<String> passwordErrors =
                violations.stream()
                        .filter(v -> v.getPropertyPath().toString().equals("password"))
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.toSet());

        assertThat(passwordErrors).isEmpty();
    }

    @Test
    void multiple_validation_errors_reported() {
        UserRegisterRequest request = new UserRegisterRequest("invalid-email", "a", "weak");

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations).hasSizeGreaterThanOrEqualTo(3);
    }
}
