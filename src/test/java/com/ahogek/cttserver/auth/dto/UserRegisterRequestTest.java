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
                new UserRegisterRequest("user@example.com", "john_doe", "Password123!", true);

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void blank_email_is_invalid() {
        UserRegisterRequest request = new UserRegisterRequest("", "john", "Password123!", true);

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("Field cannot be blank");
    }

    @Test
    void null_email_is_invalid() {
        UserRegisterRequest request = new UserRegisterRequest(null, "john", "Password123!", true);

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("Field cannot be blank");
    }

    @Test
    void invalid_email_format_is_rejected() {
        UserRegisterRequest request =
                new UserRegisterRequest("not-an-email", "john", "Password123!", true);

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("Invalid email format");
    }

    @Test
    void blank_display_name_is_invalid() {
        UserRegisterRequest request = new UserRegisterRequest("user@test.com", "", "Password123!", true);

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("Field cannot be blank");
    }

    @Test
    void display_name_with_special_chars_is_invalid() {
        UserRegisterRequest request =
                new UserRegisterRequest("user@test.com", "john@doe", "Password123!", true);

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains(
                        "Display name must be 2-50 characters and contain only Chinese, Japanese, Korean, English letters, numbers, underscores, or hyphens");
    }

    @Test
    void display_name_too_short_is_invalid() {
        UserRegisterRequest request = new UserRegisterRequest("user@test.com", "a", "Password123!", true);

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains(
                        "Display name must be 2-50 characters and contain only Chinese, Japanese, Korean, English letters, numbers, underscores, or hyphens");
    }

    @Test
    void valid_display_names_accepted() {
        assertValidDisplayName("john_doe");
        assertValidDisplayName("John-Doe");
        assertValidDisplayName("User123");
        assertValidDisplayName("张三"); // Chinese
        assertValidDisplayName("田中太郎"); // Japanese (Hiragana + Kanji)
        assertValidDisplayName("やまだ"); // Japanese (Hiragana only)
        assertValidDisplayName("タナカ"); // Japanese (Katakana only)
        assertValidDisplayName("김철수"); // Korean (Hangul)
        assertValidDisplayName("田中-san"); // Mixed: Japanese + English
    }

    private void assertValidDisplayName(String displayName) {
        UserRegisterRequest request =
                new UserRegisterRequest("user@test.com", displayName, "Password123!", true);
        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        Set<String> displayNameErrors =
                violations.stream()
                        .filter(v -> v.getPropertyPath().toString().equals("displayName"))
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.toSet());

        assertThat(displayNameErrors).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "'', blank password",
        "weak, too short password",
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa, too long password"
    })
    void invalid_password_length_is_rejected(String password, String description) {
        UserRegisterRequest request = new UserRegisterRequest("user@test.com", "john", password, true);

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .withFailMessage("Expected rejection for " + description)
                .hasSize(1)
                .extracting(ConstraintViolation::getMessage)
                .contains("Password must be 8-64 characters long");
    }

    @ParameterizedTest
    @CsvSource({
        "simple, password without uppercase or digit (too short)",
        "1234567, too short (7 chars)"
    })
    void short_passwords_are_rejected(String password, String description) {
        UserRegisterRequest request = new UserRegisterRequest("user@test.com", "john", password, true);

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations).withFailMessage("Expected rejection for " + description).hasSize(1);
    }

    @Test
    void simple_passwords_accepted_when_length_meets_requirement() {
        assertValidPassword("password");
        assertValidPassword("12345678");
        assertValidPassword("ABCDEFGH");
        assertValidPassword("P@ssw0rd");
        assertValidPassword("a".repeat(64));
    }

    private void assertValidPassword(String password) {
        UserRegisterRequest request = new UserRegisterRequest("user@test.com", "john", password, true);
        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        Set<String> passwordErrors =
                violations.stream()
                        .filter(v -> v.getPropertyPath().toString().equals("password"))
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.toSet());

        assertThat(passwordErrors).isEmpty();
    }

    @Test
    void null_password_is_invalid() {
        UserRegisterRequest request = new UserRegisterRequest("user@test.com", "john", null, true);
        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("Password must be 8-64 characters long");
    }

    @Test
    void multiple_validation_errors_reported() {
        UserRegisterRequest request = new UserRegisterRequest("invalid-email", "a", "weak", true);

        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(request);

        assertThat(violations).hasSizeGreaterThanOrEqualTo(3);
    }
}
